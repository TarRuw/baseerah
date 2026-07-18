import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import 'disbursement_detail.dart';
import 'disbursement_list.dart';

/// Bank Portal **Disbursements** screen (Smart Rescue RFP, final stage): a desktop
/// split pane — a fixed-width queue of accepted offers awaiting funding on the
/// (start) side and the selection-driven funding decision on the rest. RTL-aware.
class DisbursementsScreen extends StatelessWidget {
  const DisbursementsScreen({super.key});

  static const double _listWidth = 340;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Padding(
      padding: const EdgeInsets.all(BaseerahTokens.screenPadding),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l.bankDisbursementsTitle,
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
              color: BaseerahTokens.darkText,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 16),
          const Expanded(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SizedBox(width: _listWidth, child: DisbursementList()),
                SizedBox(width: 20),
                Expanded(child: DisbursementDetail()),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
