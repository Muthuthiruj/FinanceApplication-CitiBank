# CitiBank – Android (Clean Architecture · MVVM · Repository · Hilt)

Built from the provided `FinanceApplication` project (XML Views + Navigation Component, Kotlin) and the
33-screen CitiBank design. Open in Android Studio (Ladybug+), let Gradle sync, run on API 24+.

## Demo login
`alex.carter@citibank.demo` / `Password@123` – or the "Use demo account" button (created in your Firebase on first use).

## Backend
Firebase Authentication (email/password + email verification) and Realtime Database (live per-user data).
**Read `FIREBASE_SETUP.md` first** – you must add your own `app/google-services.json` and publish `database.rules.json`.

## Navigation / back press
Any tab → Home on back; on Home a custom "Exit CitiBank?" dialog appears; other screens go back one step.

## Architecture
```
core/      Resource, Validators, LoanCalculator, Formatters, BaseViewModel + UiEvent
domain/    model/  repository/ (interfaces)  usecase/ (Transfer, MakePayment, PayBill, DepositCheck, AddPayee)
data/      local/ (AppPrefs, BankStore)  repository/ (*Impl)  seed/SeedData  pdf/PdfStatementExporter
di/        RepositoryModule (Hilt @Binds)
ui/        auth, home, accounts, cards, payments, rewards, notifications, profile, locator, support, settings, common
```
ViewModels expose `StateFlow` UI state + one-shot `events`; Fragments only render and forward input.
Swap `data/repository/*Impl` for Retrofit-backed ones to go online – nothing above `domain/` changes.

## Screens (design → fragment)
1 Splash · 2-4 Onboarding (one ViewPager) · 5 Login · 6 Sign Up · 7 OTP · 8 Forgot Password ·
9 Terms · 10 Privacy · 11 Home · 12 Accounts · 13 Account Details · 14 Credit Card · 15 Card Controls ·
16 Make Payment · 17 Bill Pay · 18 Transfer · 19 Recipients · 20 Check Deposit · 21 Transactions ·
22 Statements (PDF) · 23 Rewards · 24 Notifications · 25 Profile · 26 Security · 27 ATM/Branch Locator ·
28 Help & Support · 29 Support Chat · 30 Loans & Credit · 31 Offers · 32 Settings
(+ Payments hub and More hub behind the bottom-nav tabs).

## Known limits
- Balances are computed on the client and written atomically; use Cloud Functions + stricter rules for production.
- "Continue with Google" shows a message – needs an OAuth client + Credential Manager.
- Locator uses a schematic map (no Maps API key); Language only offers English; Location Services is a stored preference.
- Hard-coded UI strings should move to `strings.xml` before localisation. Legacy drawables from the old wallet app are left untouched.
- Written without an Android SDK in this environment: Kotlin syntax and resource/ID references were checked statically, but run a Gradle build first and send me any errors.
