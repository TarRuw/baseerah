// QA UI-02: the "Live · N banks" badge must use the singular form at count 1
// in both languages (was "1 banks" / "1 بنوك").
import 'package:baseerah/l10n/app_localizations_ar.dart';
import 'package:baseerah/l10n/app_localizations_en.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final en = AppLocalizationsEn();
  final ar = AppLocalizationsAr();

  test('English pluralizes the bank count', () {
    expect(en.liveBanks(1), 'Live · 1 bank');
    expect(en.liveBanks(3), 'Live · 3 banks');
  });

  test('Arabic uses the singular form at count 1 and pluralizes for N', () {
    expect(ar.liveBanks(1), 'مباشر · بنك واحد');
    expect(ar.liveBanks(3), 'مباشر · 3 بنوك');
  });
}
