import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../../../theme/baseerah_theme.dart';
import 'financing_request_list.dart';
import 'financing_reply_detail.dart';

/// Bank Portal **Financing Requests** screen (Smart Rescue RFP, operator side): a
/// desktop split pane over the bank shell's sidebar — a fixed-width inbox of
/// pending consumer proposals on the (start) side and the selection-driven reply
/// form filling the rest. RTL-aware like the Applications screen: the `Row`
/// follows the ambient [Directionality], so under Arabic the list sits on the
/// right and the reply form on the left automatically.
class FinancingRequestsScreen extends StatelessWidget {
  const FinancingRequestsScreen({super.key});

  /// Fixed inbox width (matches the Applications queue column).
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
            l.bankFinancingTitle,
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
                SizedBox(width: _listWidth, child: FinancingRequestList()),
                SizedBox(width: 20),
                Expanded(child: FinancingReplyDetail()),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
