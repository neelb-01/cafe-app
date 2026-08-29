# ADL Cafe

An Android cafe ordering app: browse the menu, configure a drink, build a cart,
check out, and keep a history of past orders. Everything is persisted in a Room
database, so the cart and order history survive an app restart.

**Stack:** Kotlin · XML layouts + ViewBinding · Navigation component · Room · Coroutines/Flow · Material 3

## Screens

| Screen | What it does |
| --- | --- |
| **Menu** | Search box, horizontal category filters, list of available items with a quick-add button. |
| **Item detail** | Size selector (drinks only), quantity stepper, live total, add to cart. |
| **Cart** | Per-line quantity steppers and remove, subtotal / tax / total, empty state. |
| **Checkout** | Name, pickup vs dine-in, note for the barista, order summary. |
| **Order confirmed** | Generated order number (`ADL-1001`), totals, fulfilment message. |
| **Orders** | Past orders, newest first, with their line items and status. |

## Opening it

1. Open Android Studio → **File ▸ Open** → select this folder.
2. Let Gradle sync. Android Studio writes `local.properties` with your SDK path
   on first sync; it is intentionally not checked in.
3. Run the `app` configuration on an emulator or device (minSdk 24).

Command line, once the Android SDK path is set:

```bash
./gradlew assembleDebug   # build
./gradlew test            # unit tests
```

## Tests

Everything lives in `src/test/` and runs on the JVM — no emulator, no device:

```bash
./gradlew test
./gradlew test --tests 'com.adl.cafe.data.PricingTest'
```

| Suite | Covers |
| --- | --- |
| `PricingTest` | Plain JUnit. Integer tax arithmetic: the exact-half-cent boundary, monotonicity, overflow at the top of the `Int` range, and that the displayed rate matches the rate charged. |
| `CafeRepositoryTest` | An in-memory Room database under [Robolectric](https://robolectric.org). Cart merging under 24 concurrent adds, and order placement rolling back cleanly when a write partway through fails. |

Robolectric downloads an `android-all` jar on first run, so the initial
`./gradlew test` needs network access.

## Architecture

```
ui/<screen>/   Fragment + ViewModel (+ RecyclerView adapter)
      ↓ observes StateFlow
data/CafeRepository      the only thing the UI talks to
      ↓                  (also holds AppDatabase, for withTransaction)
data/dao/*      MenuDao · CartDao · OrderDao  (Flow-returning queries)
      ↓
data/AppDatabase         Room, 4 entities
```

- **`CafeApplication`** builds the database and repository and seeds the menu on
  first launch. Dependencies are passed by hand through `CafeViewModelFactory` —
  the project is too small for a DI framework to pay for itself.
- **Money is integer cents** everywhere (`priceCents`, `totalCents`) and only
  formatted for display by `Int.asMoney()`. No floating point arithmetic on prices.
- **`Pricing`** holds the tax rate and order-number format, so the cart, checkout
  screen and stored receipt can never disagree. The rate is basis points (850 =
  8.5%) with half-up integer rounding, and the tax row's label is derived from
  that same constant rather than hardcoded in `strings.xml`.
- **Order lines snapshot** the item name, emoji and unit price at the time of
  ordering. Editing or deleting a menu item later does not rewrite old receipts.
- **The cart merges duplicates**: adding an item that is already in the cart at
  the same size bumps the quantity instead of adding a second row (enforced by a
  unique index on `menuItemId + size`). The lookup and the write share a
  transaction, so concurrent quick-adds cannot both miss the row and collide.
- **Checkout is atomic**: the order row, its number, its lines and the cart clear
  are one transaction, so a failure partway through leaves no half-written order
  and an intact cart.

### Data model

| Entity | Notes |
| --- | --- |
| `MenuItem` | `sizable` distinguishes drinks (S/M/L, with surcharge) from food. |
| `CartItem` | References `MenuItem`; `CartLine` joins the two via `@Relation`. |
| `OrderEntity` | Totals, customer name, order type, status. |
| `OrderLine` | Snapshotted line; `OrderWithLines` joins via `@Relation`. |

Enums are persisted by name through `Converters`, so reordering an enum cannot
corrupt saved rows.

## Changing the menu

Edit `data/SeedData.kt`. Seeding only runs when the table is empty, so to pick up
changes either clear the app's storage or bump the `version` in `AppDatabase`
(it is set to `fallbackToDestructiveMigration`, so the database is rebuilt).
