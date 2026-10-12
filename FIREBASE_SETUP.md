# Firebase setup (5 minutes)

The app will not build until `app/google-services.json` exists (this is your own Firebase project's file).

1. Firebase Console → **Add project** → Add an **Android app** with package `com.example.financeapplication`
   (if you change `applicationId`, use the new one). Download **google-services.json** and put it in `app/`.
2. **Build → Authentication → Sign-in method** → enable **Email/Password**.
3. **Build → Realtime Database → Create database** (choose a region, start in *locked mode*).
   - If the region isn't `us-central1`, copy the database URL shown at the top of the Data tab and make sure
     `google-services.json` was re-downloaded after creating the database (it contains `firebase_url`).
4. **Realtime Database → Rules** → paste the contents of `database.rules.json` → **Publish**.
   Each signed-in user can only read/write `users/{their uid}`.
5. Sync Gradle and run.

## Data layout (`users/{uid}`)
```
profile/        fullName, email, phone, address
accounts/{id}   name, type (CHECKING|SAVINGS|CREDIT), number, balance, creditLimit
transactions/{id}  accountId, title, category, amount, timestamp, note
payees/{id}  bills/{id}  scheduled/{id}  notifications/{id}  offers/{id}  chat/{id}
cardControls/   quickLock, onlinePurchases, atmWithdrawals, international, transactionAlerts
rewards/points  loanApplications/{id}  reports/{id}
```
New users get a starter set (checking $2,500, savings $5,000, credit card $6,000 limit, bills, offers).
Everything after that is user-driven: add accounts, deposit checks, transfer, pay bills, close accounts.

## Behaviour notes
- Sign-up creates the Firebase user **after** Terms/Privacy, then sends a verification e-mail; the user taps the link and
  returns to the app. Login is blocked until the e-mail is verified (`REQUIRE_EMAIL_VERIFICATION` in `AuthRepositoryImpl`).
- Data is live: listeners on `users/{uid}` update every screen instantly, also across devices. Offline persistence is on, so
  writes made offline sync when the connection returns.
- Money movement is written as one atomic multi-path update. The app computes balances client-side, so for a production bank
  move this logic to Cloud Functions / server-side validation and tighten the rules (`.validate`).
- Biometric Login (Security settings) is an app-lock: it asks for fingerprint/face each time you open the app while signed in.
- "Continue with Google" is still a placeholder (needs the Google sign-in client + SHA-1 in the Firebase console).

## Demo account
- Login: `alex.carter@citibank.demo` / `Password@123` (or tap **Use demo account** on the login screen).
- The first time these credentials are used, the app creates the account in *your* Firebase project and fills it with sample
  data (checking $5,230, savings $7,250, card $1,750 owed of $6,000, six months of transactions/statements, payees, bills,
  offers, notifications, 2,450 reward points). It skips e-mail verification.
- The demo account can't have its password changed or be deleted from the app. To reset its data, delete the user in
  Firebase Authentication and the `users/{uid}` node in Realtime Database, then sign in again.
- Because it's shared by everyone with the app, don't use the demo e-mail for real sign-ups.
