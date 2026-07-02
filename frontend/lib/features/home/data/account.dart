import 'package:flutter/painting.dart';

import '../../../theme/baseerah_theme.dart';

/// Client-side view of one linked account from
/// `GET /api/v1/clients/{id}/accounts` (DESIGN §6).
///
/// Only the non-reversible [tokenizedAccountId] is exposed by the API — never a
/// raw account id (SAMA tokenization, DESIGN §9). [latestBalance] is a raw
/// amount in [currency]; formatting to `SAR`/`ر.س` is the UI's job (`Fmt.money`).
class Account {
  const Account({
    required this.id,
    required this.bankName,
    required this.displayColor,
    required this.currency,
    required this.latestBalance,
    required this.tokenizedAccountId,
  });

  final String id;
  final String bankName;
  final Color displayColor;
  final String currency;
  final double latestBalance;
  final String tokenizedAccountId;

  factory Account.fromJson(Map<String, dynamic> json) {
    return Account(
      id: json['id'] as String,
      bankName: json['bankName'] as String,
      displayColor: BaseerahTokens.hex(json['displayColor'] as String),
      currency: json['currency'] as String,
      latestBalance: (json['latestBalance'] as num).toDouble(),
      tokenizedAccountId: json['tokenizedAccountId'] as String,
    );
  }
}
