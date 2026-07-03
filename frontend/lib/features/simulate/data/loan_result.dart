import 'package:flutter/painting.dart';

import '../../../theme/baseerah_theme.dart';

/// Client-side view of `POST /api/v1/clients/{id}/loan-simulate`
/// (`LoanSimulateResponse`, FR-05, DESIGN §5.3/§6).
///
/// [dtiColor] and [verdictColor] are parsed from the server-resolved hexes
/// rather than re-derived from [dti] on the client, so the rendered bands always
/// agree with the backend's affordability verdict (same rule the stress gauge
/// follows). [installment] and [total] arrive pre-rounded to whole SAR.
class LoanResult {
  const LoanResult({
    required this.installment,
    required this.total,
    required this.dti,
    required this.dtiColor,
    required this.verdict,
    required this.verdictColor,
    required this.projectedScore,
  });

  /// Monthly instalment in whole SAR.
  final double installment;

  /// Total repayment (`installment × term`) in whole SAR.
  final double total;

  /// Debt-to-income ratio after the loan (e.g. `0.85`).
  final double dti;

  /// Palette hex for the DTI band, resolved by the backend.
  final Color dtiColor;

  /// Affordability verdict text (DESIGN §5.3).
  final String verdict;

  /// Palette hex for the verdict band, resolved by the backend.
  final Color verdictColor;

  /// Stress score projected after taking the loan (clamped `[9, 84]`).
  final int projectedScore;

  factory LoanResult.fromJson(Map<String, dynamic> json) {
    return LoanResult(
      installment: (json['installment'] as num).toDouble(),
      total: (json['total'] as num).toDouble(),
      dti: (json['dti'] as num).toDouble(),
      dtiColor: BaseerahTokens.hex(json['dtiColor'] as String),
      verdict: json['verdict'] as String,
      verdictColor: BaseerahTokens.hex(json['verdictColor'] as String),
      projectedScore: (json['projectedScore'] as num).toInt(),
    );
  }
}
