#!/usr/bin/env bash
# End-to-end check of the API the app uses, against the local Supabase stack (run by backend.yml).
# Signs up two learners and goes through auth, the REST API and row-level security over HTTP.
set -euo pipefail

status=$(supabase status -o json)
API=$(jq -r '.API_URL' <<<"$status")
KEY=$(jq -r '.PUBLISHABLE_KEY // .ANON_KEY' <<<"$status")
BOOK=00000000-0000-4000-8000-000000000001
PASSWORD='correct-horse-7'
failures=0
tmp=$(mktemp -d)

check() { # description expected actual
  if [[ "$2" == "$3" ]]; then
    echo "ok - $1"
  else
    echo "not ok - $1 (expected '$2', got '$3')"
    failures=$((failures + 1))
  fi
}

# request METHOD PATH TOKEN [BODY] [HEADER]... → sets CODE, BODY and HEADERS.
request() {
  local method=$1 path=$2 token=$3 body=${4:-}
  shift 4 2>/dev/null || shift $#
  local args=(-s -o "$tmp/body" -D "$tmp/headers" -w '%{http_code}' -X "$method" "$API$path"
              -H "apikey: $KEY" -H 'Content-Type: application/json')
  if [[ -n "$token" ]]; then
    args+=(-H "Authorization: Bearer $token")
  elif [[ "$KEY" == eyJ* ]]; then
    args+=(-H "Authorization: Bearer $KEY")  # legacy anon key
  fi
  for header in "$@"; do args+=(-H "$header"); done
  [[ -n "$body" ]] && args+=(-d "$body")
  CODE=$(curl "${args[@]}")
  BODY=$(cat "$tmp/body")
  HEADERS=$(tr -d '\r' < "$tmp/headers")
}

sign_up() { # email → prints access token
  request POST /auth/v1/signup "" "{\"email\": \"$1\", \"password\": \"$PASSWORD\"}"
  [[ "$CODE" == 200 ]] || { echo "sign-up of $1 failed: $CODE $BODY" >&2; exit 1; }
  jq -r '.access_token' <<<"$BODY"
}

A=$(sign_up learner-a@example.com)
B=$(sign_up learner-b@example.com)
check "sign-up returns a session" "true" "$([[ ${#A} -gt 100 ]] && echo true || echo false)"

# Anonymous clients get nothing.
request GET '/rest/v1/words?select=headword&limit=1' ""
check "anonymous clients cannot read words" "denied" "$([[ $CODE == 401 || $CODE == 403 ]] && echo denied || echo "$CODE")"

# Words and the system book.
request GET '/rest/v1/words?select=headword&limit=1' "$A" "" 'Prefer: count=exact'
check "a learner reads words" "4801" "$(grep -i '^content-range:' <<<"$HEADERS" | sed 's/.*\///')"
request GET '/rest/v1/words?select=id&limit=5000' "$A" ""
check "one request returns at most 1000 rows (the app pages)" "1000" "$(jq length <<<"$BODY")"
request GET "/rest/v1/book_entries?select=position,headword,words(display,translation_zh)&book_id=eq.$BOOK&order=position&limit=2" "$A" ""
check "the book downloads in study order with its words" "government government" \
  "$(jq -r '.[0] | "\(.headword) \(.words.display)"' <<<"$BODY")"

# Answers: append-only, safe to upload twice.
logs='[{"id": "10000000-0000-4000-8000-000000000001", "headword": "abandon", "rating": 3, "mode": "choice",
        "reviewed_at": "2026-09-30T10:00:00Z", "study_day": 20361, "duration_ms": 4000},
       {"id": "10000000-0000-4000-8000-000000000002", "headword": "abandon", "rating": 3, "mode": "recognition",
        "reviewed_at": "2026-09-30T10:10:00Z", "study_day": 20361, "duration_ms": 2500}]'
request POST /rest/v1/review_logs "$A" "$logs" 'Prefer: resolution=ignore-duplicates,return=minimal'
check "a learner uploads answers" "201" "$CODE"
request POST /rest/v1/review_logs "$A" "$logs" 'Prefer: resolution=ignore-duplicates,return=minimal'
check "uploading the same answers again is harmless" "201" "$CODE"
request GET '/rest/v1/review_logs?select=id,received_at&received_at=gt.2026-01-01T00:00:00Z&order=received_at' "$A" ""
check "pulling answers since a cursor returns each once" "2" "$(jq length <<<"$BODY")"
request PATCH '/rest/v1/review_logs?id=eq.10000000-0000-4000-8000-000000000001' "$A" '{"rating": 1}'
check "answers cannot be changed" "denied" "$([[ $CODE == 401 || $CODE == 403 ]] && echo denied || echo "$CODE")"
request GET '/rest/v1/review_logs?select=id' "$B" ""
check "another learner sees none of them" "0" "$(jq length <<<"$BODY")"

# Favourites and settings: newest edit wins.
request POST /rest/v1/favorites "$A" '{"headword": "abandon", "is_favorite": true, "updated_at": "2026-09-30T12:00:00Z"}' \
  'Prefer: resolution=merge-duplicates,return=minimal'
check "a learner marks a favourite" "201" "$CODE"
request POST /rest/v1/favorites "$A" '{"headword": "abandon", "is_favorite": false, "updated_at": "2026-09-30T11:00:00Z"}' \
  'Prefer: resolution=merge-duplicates,return=minimal'
request GET '/rest/v1/favorites?select=is_favorite&headword=eq.abandon' "$A" ""
check "an older edit from another device is ignored" "true" "$(jq -r '.[0].is_favorite' <<<"$BODY")"
request POST /rest/v1/user_settings "$A" "{\"book_id\": \"$BOOK\", \"daily_new_words\": 30, \"updated_at\": \"2026-09-30T12:00:00Z\"}" \
  'Prefer: resolution=merge-duplicates,return=representation'
check "a learner saves settings" "30" "$(jq -r '.[0].daily_new_words' <<<"$BODY")"

# Account deletion.
request POST /rest/v1/rpc/delete_my_account "$A" '{}'
check "a learner deletes their account" "deleted" "$([[ $CODE == 200 || $CODE == 204 ]] && echo deleted || echo "$CODE $BODY")"
request POST '/auth/v1/token?grant_type=password' "" "{\"email\": \"learner-a@example.com\", \"password\": \"$PASSWORD\"}"
check "the deleted account can no longer sign in" "400" "$CODE"
request POST '/auth/v1/token?grant_type=password' "" "{\"email\": \"learner-b@example.com\", \"password\": \"$PASSWORD\"}"
check "other accounts still sign in" "200" "$CODE"

rm -rf "$tmp"
if (( failures > 0 )); then
  echo "$failures API check(s) failed"
  exit 1
fi
echo "All API checks passed"
