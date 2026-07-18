import 'package:flutter/material.dart';

import '../../../core/format.dart';
import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import '../data/account.dart';

/// The client's money across every linked account: the sum of each account's own
/// latest balance, with the account count beneath it.
///
/// Summing [Account.latestBalance] is the same definition the backend's engines
/// use for a client's buffer — each account's most recent closing balance, added
/// together — so this figure and the stress score's runway agree. Summing any
/// single account's balance (or the newest transaction's closing balance across
/// the merged set) would report one arbitrary account as the whole client.
///
/// It sits under the forecast and above the accounts it totals, deliberately
/// below the gauge and the deficit warning: a healthy-looking balance is exactly
/// what Baseerah exists to look past, so it summarises the list rather than
/// leading the screen.
///
/// Currency: [Fmt.money] appends the one localized unit the product ships
/// (SAR/ر.س), so — like every other amount on Home — this assumes the SAR-only
/// feed the API serves. A mixed-currency client would need per-currency totals
/// here and a currency-aware [Fmt.money] everywhere else.
class TotalBalanceCard extends StatelessWidget {
  const TotalBalanceCard({super.key, required this.accounts, required this.fmt});

  final List<Account> accounts;
  final Fmt fmt;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final total = accounts.fold<double>(0, (sum, a) => sum + a.latestBalance);

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(BaseerahTokens.radiusCard),
        boxShadow: BaseerahTokens.shadowSoft,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: BaseerahTokens.teal.withValues(alpha: 0.12),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.account_balance_wallet_outlined,
                  size: 20,
                  color: BaseerahTokens.teal,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  l.totalBalance,
                  style: textTheme.bodyMedium?.copyWith(
                    color: BaseerahTokens.muted,
                  ),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            fmt.money(total),
            style: textTheme.headlineSmall?.copyWith(
              color: BaseerahTokens.darkText,
              fontWeight: FontWeight.w700,
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 4),
          Text(
            l.acrossAccounts(accounts.length),
            style: textTheme.bodySmall?.copyWith(color: BaseerahTokens.muted),
          ),
        ],
      ),
    );
  }
}
