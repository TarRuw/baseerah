import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// Single source of truth for the Baseerah visual design system (DESIGN.md §8).
///
/// Both shells (consumer + bank) consume these tokens — never hardcode a colour,
/// font, radius, shadow or gradient in a screen file. Add new tokens here.
class BaseerahTokens {
  BaseerahTokens._();

  // ── Palette ───────────────────────────────────────────────────────────────
  /// Primary teal (gradient start) and its darker gradient end.
  static const Color teal = Color(0xFF0E6B54);
  static const Color tealDark = Color(0xFF0A3D33);
  static const Color gold = Color(0xFFC4A24C);
  static const Color goldDark = Color(0xFFA8863A);
  static const Color alertRed = Color(0xFFE0574F);
  static const Color successGreen = Color(0xFF1D9E63);
  static const Color warningOrange = Color(0xFFE5A63A);
  static const Color darkText = Color(0xFF13241F);
  static const Color muted = Color(0xFF6E7F78);
  static const Color lightBg = Color(0xFFF6F4EE);
  static const Color appDarkBg = Color(0xFF0C1512);

  // Red-warning gradient stops (distinct from [alertRed]).
  static const Color redWarningStart = Color(0xFFC0433C);
  static const Color redWarningEnd = Color(0xFF8F2F2A);

  // Desktop backdrop radial-gradient stops (bank portal).
  static const Color desktopBackdropInner = Color(0xFF243530);
  static const Color desktopBackdropOuter = Color(0xFF161E1A);

  // ── Radii ─────────────────────────────────────────────────────────────────
  static const double radiusPhoneFrame = 52;
  static const double radiusPhoneScreen = 40;
  static const double radiusCard = 22; // cards 20–24
  static const double radiusControl = 12; // buttons/inputs 9–14
  // Avatars: circle → use BoxShape.circle / BorderRadius none.

  // ── Shadows ───────────────────────────────────────────────────────────────
  /// Soft: `0 2px 6px rgba(0,0,0,.06)`.
  static const List<BoxShadow> shadowSoft = [
    BoxShadow(color: Color(0x0F000000), offset: Offset(0, 2), blurRadius: 6),
  ];

  /// Medium: `0 8px 24px -16px rgba(10,61,51,.25)`.
  /// (Flutter has no spread-negative; approximated with a tight blur.)
  static const List<BoxShadow> shadowMedium = [
    BoxShadow(
      color: Color(0x400A3D33),
      offset: Offset(0, 8),
      blurRadius: 24,
      spreadRadius: -16,
    ),
  ];

  // ── Gradients (all 135°) ────────────────────────────────────────────────────
  static const Gradient tealGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [teal, tealDark],
  );

  static const Gradient goldGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [gold, goldDark],
  );

  static const Gradient redWarningGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [redWarningStart, redWarningEnd],
  );

  /// Bank portal desktop backdrop: `radial-gradient(1200x700 at 50% -10%)`.
  static const Gradient desktopBackdrop = RadialGradient(
    center: Alignment(0, -1.2),
    radius: 1.4,
    colors: [desktopBackdropInner, desktopBackdropOuter],
  );

  // ── Motion (DESIGN §8) ──────────────────────────────────────────────────────
  /// Stress-score gauge marker animation: "1000ms cubic ease-out".
  static const Duration scoreAnimation = Duration(milliseconds: 1000);
  static const Curve scoreAnimationCurve = Curves.easeOutCubic;

  /// `bsr-pulse` — 2.4s attention pulse on the liquidity-deficit warning card.
  static const Duration pulse = Duration(milliseconds: 2400);

  // ── Spacing ─────────────────────────────────────────────────────────────────
  /// Default gutter between stacked cards on the phone screens.
  static const double gap = 16;
  static const double screenPadding = 20;

  /// Parse a `#RRGGBB` hex (as served by the stress-score API `color` field) to a
  /// [Color]. Falls back to [muted] for anything malformed so the UI never throws
  /// on an unexpected payload.
  static Color hex(String value) {
    final cleaned = value.replaceFirst('#', '').trim();
    if (cleaned.length != 6) return muted;
    final parsed = int.tryParse(cleaned, radix: 16);
    if (parsed == null) return muted;
    return Color(0xFF000000 | parsed);
  }
}

/// Builds the [ThemeData] both shells share. Fonts come from google_fonts
/// (`IBM Plex Sans` for Latin, `IBM Plex Sans Arabic` for Arabic).
class BaseerahTheme {
  BaseerahTheme._();

  /// Text theme for the given [locale]; swaps the Arabic font when `ar`.
  static TextTheme _textTheme(Brightness brightness, Locale? locale) {
    final base = ThemeData(brightness: brightness).textTheme;
    final isArabic = locale?.languageCode == 'ar';
    final themed = isArabic
        ? GoogleFonts.ibmPlexSansArabicTextTheme(base)
        : GoogleFonts.ibmPlexSansTextTheme(base);
    return themed.apply(
      bodyColor: BaseerahTokens.darkText,
      displayColor: BaseerahTokens.darkText,
    );
  }

  /// Light theme (the app's primary appearance). [locale] selects the font.
  static ThemeData light(Locale locale) {
    final colorScheme = ColorScheme.fromSeed(
      seedColor: BaseerahTokens.teal,
      brightness: Brightness.light,
    ).copyWith(
      primary: BaseerahTokens.teal,
      secondary: BaseerahTokens.gold,
      error: BaseerahTokens.alertRed,
      surface: Colors.white,
      onSurface: BaseerahTokens.darkText,
    );

    return ThemeData(
      useMaterial3: true,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: BaseerahTokens.lightBg,
      textTheme: _textTheme(Brightness.light, locale),
      appBarTheme: const AppBarTheme(
        backgroundColor: BaseerahTokens.teal,
        foregroundColor: Colors.white,
        elevation: 0,
        centerTitle: true,
      ),
      cardTheme: CardThemeData(
        color: Colors.white,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: BaseerahTokens.teal,
          foregroundColor: Colors.white,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(BaseerahTokens.radiusControl),
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: Colors.white,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(BaseerahTokens.radiusControl),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: Colors.white,
        indicatorColor: BaseerahTokens.teal.withValues(alpha: 0.12),
      ),
    );
  }
}
