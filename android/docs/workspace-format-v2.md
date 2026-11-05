# Workspace format v2

Native app-private storage and JSON export use this format. Import UI is still a later
task; `WorkspaceJson.decodeDocument` is the shared decoder for that future flow.
All fields listed below are required. v2 rejects unknown fields and wrong JSON types;
it never coerces strings into Booleans or numbers. Root field order is insignificant.

## Schema

| Object | Fields |
| --- | --- |
| Workspace | `version`: 2; `onboarded`: Boolean; `theme`: SYSTEM/LIGHT/DARK; `name`, `bio`: strings; `habits`, `commits`: arrays; `calendar`: object |
| Habit | `id`, `name`, `description`, `unit`: strings; `icon`: BOOK/MOVEMENT/CODE/WATER/CALM/JOURNAL; `color`: GREEN/ORANGE/BLUE/TEAL/PURPLE/PINK; `createdOn`: ISO local date; `versions`: nonempty array |
| Goal version | `date`: ISO local date; `target`: positive decimal; `status`: ACTIVE/PAUSED/ARCHIVED; `schedule`: object |
| Daily schedule | `kind`: DAILY, with no other fields |
| Selected weekdays | `kind`: SELECTED_WEEKDAYS; `weekdays`: nonempty array of distinct MONDAY–SUNDAY enum names |
| Weekly quota | `kind`: WEEKLY_QUOTA; `days`: integer 1–7 |
| Commit | `id`, `habitId`, `note`: strings; `amount`: positive decimal; `date`: authoritative ISO local date; `timestamp`: ISO instant |
| Calendar | `initialWeekStart`: MONDAY–SUNDAY; `transitions`: ordered array; `pending`: transition object or explicit null |
| Week transition | `boundary`: ISO local date; `weekStart`: MONDAY–SUNDAY |

Habit IDs and commit IDs are unique within their respective collections, nonblank, and
at most 128 Kotlin string code units. Commit references must resolve. Versions start
on the creation date and have strictly increasing effective dates. Targets/amounts are
0.001–1,000,000, mathematically representable with at most three decimal places. Encoding
and decoding reject extra precision rather than rounding. Equivalent decimal/exponent
representations within the bounds are accepted.

Calendar transitions follow `WeekCalendar` validation: each boundary uses the preceding
effective weekday, destinations differ, and bridges do not overlap. Chained bridges are
valid. The decoder does **not** advance pending transitions using the current device date;
an elapsed pending boundary is valid serialized state and retains its original dates.

An unset profile is represented by empty strings. Display names allow a trimmed length
of 0–40; bio length is at most 160. Habit name/unit/description retain v1's trimmed-length
bounds of 1–48 / 1–20 / 0–160; notes retain the raw 200-character bound. Decoding never trims
these strings. Unchanged legacy padded strings/units also survive ordinary repo editing.

## Legacy migration

- Decode v1 using strict syntax and required field types. Valid explicit `darkTheme`
  maps to DARK/LIGHT, never SYSTEM. `signedIn` is type-checked then discarded.
- Set local onboarding to `legacy.onboarded || habits.isNotEmpty() || commits.isNotEmpty()`.
  Existing populated history opens locally without simulated sign-in.
- Add Daily to every version; initialize a Monday calendar without transitions/pending.
  Sort legacy versions by effective date without dropping versions; duplicates remain invalid.
- Preserve IDs (including `sample-*`), profile/default-looking strings, notes, units,
  amounts, local dates and timestamps. Retained commits may precede creation, be future-dated,
  disagree with their timestamp's calendar date, or belong to a date later paused/archived.
  Do not replay them through today's new-log commands.
- v1's ignored extra metadata is not introduced into the v2 domain; the exact original
  file is retained. Noncoercible/incompatible records, including values that the old codec
  would silently round, enter `LEGACY_INCOMPATIBLE` recovery. Nothing is silently repaired.
- `DecodedWorkspace.sourceVersion` distinguishes migration input from v2. The store
  independently preserves the original, writes v2, and verifies both actual v2 format and
  candidate values. Unchanged v1 bytes cannot falsely verify the write. Subsequent stale
  checks retain the observed file bytes, including any value-equivalent formatting.

## Capacity and recovery

Supported JSON input/output remains provisionally bounded to 32 MiB UTF-8, 1,000 habits,
100,000 commits, 100,000 **total** dated configurations (habit versions + calendar transitions
+ a nonnull pending transition), 128-code-unit IDs and depth 8. The initial undated calendar
weekday is not a dated configuration. These bounds still require Task 13 minimum-device
memory certification; JVM tests are not a substitute.

Strict streaming preflight checks syntax/depth before constructing domain records, then a
second streaming pass dispatches by source version. There is no generic whole-document tree.
Internal malformed-input guards allow at most 16 fields/object, 2,109,032 JSON values, and
128 characters in schema numeric lexemes; ordinary encoded supported records fit those guards.
Output is counted as escaped UTF-8 before buffer growth. Export validates/encodes the entire
snapshot **before** opening its provider destination in truncating mode.

Physical reads are bounded too. An oversized original is streamed to a full, synced,
length/SHA-256-read-back-verified RECOVERY archive; it is never returned as a prefix. Verified
archive reuse is attempted before allocating another staging file. If preservation fails,
the source remains untouched and no replacement is permitted. Oversized primary storage
stays non-writable until reconciled; its raw archive can be exported without the JSON limit.
An independently retained oversized last-good archive does not block a healthy primary.

Before `AtomicFile.openRead` may recover/delete sidecars, existing base/`.bak`/`.new` evidence
is independently retained. An interrupted first write with only `.new` is a recovery state,
not a new workspace. Its archived bytes can be exported; explicitly confirmed start-over
re-preserves that sidecar before replacement. The platform journey is compiled but not yet
executed on a provisioned disposable device at this checkpoint.

Recovery exports freeze their byte snapshot or immutable retained archive **before** opening
the destination, so a concurrent reset/retry cannot switch the exported source. Failed
staging files are not complete recovery snapshots and cannot shadow finalized archives.
Recovery retries must preserve outstanding originals/candidates before replacing their
in-memory slots. Independent RECOVERY archives are never rotated like LAST_GOOD.

These measures do not guarantee survival of hardware/storage failure. App-private copies
remain covered by existing automatic-backup/device-transfer exclusions. External document
providers are not atomic; write failure may leave an incomplete exported file.

## Intermediate-build boundary

The model contains no simulated identity. Temporary `entryOpen` navigation state and the
old entry/demo UI remain until Task 7. SYSTEM resolves at the application theme/system-bar
boundary; full three-choice Settings arrives later. The old repo form retains imported
schedules rather than converting them to Daily, but schedule selection/accurate schedule
presentation are later tasks. No sample-bearing intermediate APK is a personal-use delivery.
