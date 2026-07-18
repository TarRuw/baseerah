import 'package:flutter/material.dart';

import '../data/account.dart';

/// The bank's mark for an account row: its bundled logo, or a monogram when there
/// isn't one.
///
/// The asset is resolved from the server's [Account.logoSlug] rather than by
/// matching on the bank's name, so adding a bank is a directory row plus a file —
/// no Dart change. Assets are bundled, never fetched: the accounts list has to
/// render offline and on first paint, and the app must not call a third-party host
/// at runtime.
///
/// Falls back to a monogram in both directions — a bank the directory doesn't know
/// (`logoSlug == null`) and an asset that fails to decode — so the row always
/// renders something branded. That fallback is also the escape hatch for the
/// trademark question in docs/bank-logos.md: delete the files and every
/// bank degrades to a monogram with no code change.
class BankLogo extends StatelessWidget {
  const BankLogo({super.key, required this.account, this.size = 36});

  final Account account;
  final double size;

  @override
  Widget build(BuildContext context) {
    final slug = account.logoSlug;
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        // A white chip: these marks are drawn for light backgrounds, and it keeps
        // eight different brands reading as one list.
        color: slug == null
            ? account.displayColor.withValues(alpha: 0.15)
            : Colors.white,
        shape: BoxShape.circle,
        border: Border.all(color: const Color(0x14000000)),
      ),
      clipBehavior: Clip.antiAlias,
      child: slug == null
          ? _Monogram(account: account, size: size)
          : Padding(
              // Inset so a square mark doesn't touch the circle's edge.
              padding: EdgeInsets.all(size * 0.18),
              child: Image.asset(
                'assets/banks/$slug.png',
                fit: BoxFit.contain,
                filterQuality: FilterQuality.medium,
                errorBuilder: (context, _, __) =>
                    _Monogram(account: account, size: size),
              ),
            ),
    );
  }
}

/// The bank's initial in its own colour — what shows when there is no logo.
class _Monogram extends StatelessWidget {
  const _Monogram({required this.account, required this.size});

  final Account account;
  final double size;

  @override
  Widget build(BuildContext context) {
    // First letter of the bank's name. Latin and Arabic names both give a sensible
    // glyph, so this needs no locale handling.
    final name = account.bankName.trim();
    final initial = name.isEmpty ? '?' : name.characters.first;
    return Center(
      child: Text(
        initial,
        style: TextStyle(
          color: account.displayColor,
          fontWeight: FontWeight.w700,
          fontSize: size * 0.42,
        ),
      ),
    );
  }
}
