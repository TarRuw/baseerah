/// Client-side view of `POST /api/v1/clients/{id}/chat/invoice`
/// (`InvoiceParseResult`, FR-03, DESIGN §9).
///
/// In mock mode the backend returns a deterministic stub (no OCR); this holds
/// its shape so the chat tab can render the parsed action as an AI bubble. The
/// amount is left as a raw number — the widget formats it with `Fmt.money`.
class InvoiceResult {
  const InvoiceResult({
    required this.merchant,
    required this.amount,
    required this.category,
    required this.suggestedAction,
  });

  final String merchant;
  final double amount;
  final String category;
  final String suggestedAction;

  factory InvoiceResult.fromJson(Map<String, dynamic> json) {
    return InvoiceResult(
      merchant: json['merchant'] as String,
      amount: (json['amount'] as num).toDouble(),
      category: json['category'] as String,
      suggestedAction: json['suggestedAction'] as String,
    );
  }
}
