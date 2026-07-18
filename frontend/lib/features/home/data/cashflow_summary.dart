/// Client-side view of `GET /api/v1/clients/{id}/cashflow-summary` (DESIGN §6):
/// the client's average monthly income and spending (SAR) over the trailing
/// stress-score window. Raw amounts — formatting to `SAR`/`ر.س` is the UI's job
/// (`Fmt.money`).
class CashflowSummary {
  const CashflowSummary({
    required this.avgMonthlyIncome,
    required this.avgMonthlyExpense,
  });

  final double avgMonthlyIncome;
  final double avgMonthlyExpense;

  factory CashflowSummary.fromJson(Map<String, dynamic> json) {
    return CashflowSummary(
      avgMonthlyIncome: (json['avgMonthlyIncome'] as num).toDouble(),
      avgMonthlyExpense: (json['avgMonthlyExpense'] as num).toDouble(),
    );
  }
}
