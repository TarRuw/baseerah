import 'package:flutter/material.dart';

import '../../../core/format.dart';
import '../../../theme/baseerah_theme.dart';
import '../data/account.dart';
import 'bank_logo.dart';

/// The client's linked accounts (DESIGN §7.1): one row per account showing the
/// bank's mark, its name, and the latest balance formatted with [Fmt.money]
/// (SAR/ر.س by locale). Non-scrolling — the Home screen owns scroll.
class LinkedAccountsList extends StatelessWidget {
  const LinkedAccountsList({
    super.key,
    required this.accounts,
    required this.fmt,
  });

  final List<Account> accounts;
  final Fmt fmt;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;

    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
        boxShadow: BaseerahTokens.shadowSoft,
      ),
      child: Column(
        children: [
          for (var i = 0; i < accounts.length; i++) ...[
            if (i > 0)
              const Divider(height: 1, indent: 16, endIndent: 16),
            _AccountRow(account: accounts[i], fmt: fmt, textTheme: textTheme),
          ],
        ],
      ),
    );
  }
}

class _AccountRow extends StatelessWidget {
  const _AccountRow({
    required this.account,
    required this.fmt,
    required this.textTheme,
  });

  final Account account;
  final Fmt fmt;
  final TextTheme textTheme;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Row(
        children: [
          BankLogo(account: account),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              account.bankName,
              style: textTheme.bodyLarge?.copyWith(
                color: BaseerahTokens.darkText,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          Text(
            fmt.money(account.latestBalance),
            style: textTheme.bodyLarge?.copyWith(
              color: BaseerahTokens.darkText,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}
