# UI/UX guidelines v0

**Status:** `PROPOSED`. The project has a basic XML `activity_main.xml`, not a finalized design system. Do not claim visual approval without screenshots from the team.

## Principles

- Java + XML layouts; keep one simple, consistent navigation approach agreed with integration owner.
- Reuse shared Material/AppCompat widgets already available in Gradle; seek approval before adding libraries.
- Use one approved color palette, typography hierarchy and spacing scale; keep text readable in Vietnamese with sufficient contrast.
- Prefix feature-specific resources (`resident_`, `manager_`) to minimize collisions.
- Define labels, error messages and accessibility text in resources rather than hard-coding strings in Java.

## Minimum states for every screen

| State | Required behavior |
|---|---|
| Loading | Disable duplicate submit; show progress |
| Empty | Explain no reports yet and next action |
| Error | Clear message and retry path, without leaking internals |
| Offline | Explain data may not be current / queued status only if implemented |
| Valid data | Show timestamps, priority and normalized status consistently |

## Resident screen set

1. Report list (`resident_list`): own reports only, clear status badge, open detail, start new report.
2. Report form (`resident_create`): title, description, location, priority; inline validation, submit loading/success/error.
3. Report detail (`resident_detail`): full fields, assigned handler, deadline, timeline.
4. Completion confirmation (`resident_confirm`): show `Đã xong` / `Chưa xong` **only** when own report is `COMPLETED`.
5. Image picker/camera: optional follow-on only after MVP approved.

## To decide in week 1

Color tokens, typography, navigation patterns, screen naming, responsive target devices, date/time display, button text and handling of overdue incidents. Share one lightweight mock before parallel UI work.
