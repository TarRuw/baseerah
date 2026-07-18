import '../../../l10n/app_localizations.dart';

/// Maps a declared-expense category **key** to its localized label — the single
/// source both the picker (Step 11.5) and the expense list render from, so the
/// language toggle re-renders instantly with **no refetch** (the API returns
/// keys; the ARB owns the labels — a locked Phase-11 decision).
///
/// [allKeys] mirrors the backend `Category.declarableExpenseCategories()` in
/// enum-declaration order — the exact set `GET /api/v1/categories/expense`
/// returns (the 16 expense categories plus `OTHER`). It exists so the
/// label-coverage test can assert every backend key has a non-null, non-raw
/// label in both locales without a live backend; if a new backend category
/// ships without a matching ARB key, [labelFor] returns the raw key and that
/// test fails loudly rather than the UI silently rendering `SCREAMING_SNAKE`.
class ExpenseCategory {
  const ExpenseCategory(this.key);

  /// The canonical category key (e.g. `UTILITIES`, `OTHER`).
  final String key;

  /// The key stored for a "catch-all" declared expense — a real, storable
  /// choice (cash rent, family support), not merely a fallback (locked
  /// decision #3). The picker offers it as a first-class option.
  static const String otherKey = 'OTHER';

  /// Whether this is the catch-all `OTHER` choice (the picker renders it last /
  /// pairs it with a free-text label prompt).
  bool get isOther => key == otherKey;

  /// This category's localized label in the active locale.
  String label(AppLocalizations l10n) => labelFor(l10n, key);

  /// The declarable-expense category keys, in the backend's enum order. Keep in
  /// sync with `domain/kernel/Category.declarableExpenseCategories()`; the
  /// coverage test guards drift.
  static const List<String> allKeys = [
    'GROCERIES',
    'RESTAURANTS_DINING',
    'RESTAURANTS_LUXURY',
    'TRANSPORTATION',
    'TRAVEL_FLIGHTS',
    'TRAVEL_HOTELS',
    'SHOPPING',
    'SHOPPING_LUXURY',
    'TELECOM',
    'UTILITIES',
    'HEALTHCARE',
    'HEALTH_FITNESS',
    'EDUCATION_BOOKS',
    'ENTERTAINMENT_SUBSCRIPTIONS',
    'SOFTWARE_SUBSCRIPTIONS',
    'BUSINESS_EXPENSES',
    otherKey,
  ];

  /// Resolve a category key to its localized label. Every key in [allKeys] has
  /// an explicit ARB-backed case; an **unmapped** key returns the key itself
  /// (visible, so the coverage test catches it) rather than `null` — in
  /// production the endpoint only ever returns keys from [allKeys], so this
  /// fallback never renders.
  static String labelFor(AppLocalizations l10n, String key) {
    switch (key) {
      case 'GROCERIES':
        return l10n.categoryGroceries;
      case 'RESTAURANTS_DINING':
        return l10n.categoryRestaurantsDining;
      case 'RESTAURANTS_LUXURY':
        return l10n.categoryRestaurantsLuxury;
      case 'TRANSPORTATION':
        return l10n.categoryTransportation;
      case 'TRAVEL_FLIGHTS':
        return l10n.categoryTravelFlights;
      case 'TRAVEL_HOTELS':
        return l10n.categoryTravelHotels;
      case 'SHOPPING':
        return l10n.categoryShopping;
      case 'SHOPPING_LUXURY':
        return l10n.categoryShoppingLuxury;
      case 'TELECOM':
        return l10n.categoryTelecom;
      case 'UTILITIES':
        return l10n.categoryUtilities;
      case 'HEALTHCARE':
        return l10n.categoryHealthcare;
      case 'HEALTH_FITNESS':
        return l10n.categoryHealthFitness;
      case 'EDUCATION_BOOKS':
        return l10n.categoryEducationBooks;
      case 'ENTERTAINMENT_SUBSCRIPTIONS':
        return l10n.categoryEntertainmentSubscriptions;
      case 'SOFTWARE_SUBSCRIPTIONS':
        return l10n.categorySoftwareSubscriptions;
      case 'BUSINESS_EXPENSES':
        return l10n.categoryBusinessExpenses;
      case otherKey:
        return l10n.categoryOther;
      default:
        return key;
    }
  }
}
