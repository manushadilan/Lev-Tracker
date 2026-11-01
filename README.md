# 🌿 Lev-tracker

A simple, offline Android app for keeping track of your leaves at work. Add leaves, browse them by month or on a calendar, see monthly totals on a graph, and back up your data as CSV or PDF. Available in **English** and **සිංහල**.

## Features

- **Leave categories:** Casual, Sick, Duty, Half Day and Short Leave
- **Day counting:** Casual, Sick and Duty count as 1 day, Half Day as 0.5, and Short Leave as 0
- **Add, edit and delete** leaves (tap an entry to edit, long-press to delete)
- **Multiple days:** an optional "Add multiple days" switch for Casual, Sick and Duty creates one entry per day, up to 10 days at once
- **No duplicates:** only one leave is allowed per date
- **Short Leave limit:** at most 2 Short Leave entries per month
- **No future dates or weekends:** future dates, Saturdays and Sundays can't be selected
- **Yearly totals:** a summary of leaves for every year
- **Monthly grouping:** leaves are grouped by month in ascending date order, with a total per month
- **Filters:** switch year, or pick a single month (new leaves open on the selected month)
- **Calendar tab:** a month grid with days coloured by category and a colour key
- **Graph:** bar chart of leave days per month for the selected year
- **Yearly balances:** set yearly limits for Casual and Sick and see how many days are left
- **Export:** CSV and PDF of the current year or month
- **Import:** restore from a CSV exported by the app (duplicates, future dates, rows over the Short Leave limit and invalid rows are skipped)
- **Appearance:** light, dark or system theme, and five colour schemes (brown, green, blue, purple, red)
- **Languages:** English and Sinhala, switchable from the menu

## Tech stack

- Kotlin, Android Views with Material 3 (Material Components)
- Room (SQLite) for local storage, with coroutines
- Per-app language support via `AppCompatDelegate`
- Min SDK 24, target SDK 34

## Download the APK (GitHub Actions)

This repository includes a workflow that builds a debug APK.

1. Open the **Actions** tab and select **Build APK** (it runs on every push, or run it manually).
2. When the run finishes, download **LeaveTracker-apk** from the Artifacts section.
3. Unzip it, copy `app-debug.apk` to your phone and install it. Allow "Install unknown apps" if asked.

## Build locally

1. Open the project in Android Studio and let Gradle sync.
2. Run it on a device or emulator, or use **Build > Build APK(s)**.
3. The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

Or from the command line (Gradle 8.7+, JDK 17):

```bash
gradle assembleDebug
```

## Using the app

| Action | How |
| --- | --- |
| Add a leave | Tap **Add leave**, choose category, date and an optional reason |
| Add several days | Choose Casual, Sick or Duty, turn on **Add multiple days**, set From and To |
| Edit / delete | Tap an entry to edit, long-press to delete |
| Change period | Use the ‹ › year buttons and the month button |
| Calendar view | Open the **Calendar** tab at the bottom |
| Graph | Tap **Graph** in the top bar |
| Yearly totals, balances, import/export, appearance, language | Open the ⋮ menu |

CSV format used for export and import:

```csv
Date,Category,Days,Reason
2026-10-05,Casual,1,"Family event"
```

Dates are `yyyy-MM-dd`, and category must be one of `Casual`, `Sick`, `Duty`, `Half Day` or `Short Leave`. The Days column is ignored on import because days are worked out from the category.

## Project structure

```
app/src/main/
├── java/com/example/leavetracker/
│   ├── MainActivity.kt   # UI, calendar, export/import, language
│   ├── App.kt            # Saved theme applied at startup
│   ├── Db.kt             # Room entity, DAO and database
│   └── ChartView.kt      # Monthly bar chart
└── res/                  # Layouts, menu, icons, strings (en, si)
.github/workflows/build.yml   # Builds the debug APK
```

## Notes

- All data stays on the device. The app has no internet access and no account.
- Yearly balances are stored in app preferences and are not included in CSV exports.
- Sinhala text can be edited in `app/src/main/res/values-si/strings.xml`.
