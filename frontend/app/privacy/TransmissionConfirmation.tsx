"use client";

export type TransmissionPreview = {
  id: string;
  confirmationToken: string;
  operation: "OPENAI_INBOX_STRUCTURING" | "OPENAI_WEEKLY_REFLECTION" | "LIVE_JOB_PAGE_FETCH";
  destination: string;
  purpose: string;
  minimizedFields: string[];
  expiresAt: string;
};

export function TransmissionConfirmation({ preview, outboundSummary, busy, onCancel, onConfirm }: {
  preview: TransmissionPreview;
  outboundSummary: string;
  busy: boolean;
  onCancel: () => void;
  onConfirm: (confirmationToken: string) => void;
}) {
  return <section className="transmission-confirmation" role="alertdialog" aria-modal="true"
    aria-labelledby="transmission-confirmation-title">
    <header><p className="eyebrow">Connected transmission preview</p>
      <h3 id="transmission-confirmation-title">Review exactly what leaves the local-only boundary</h3></header>
    <dl><div><dt>Destination</dt><dd>{preview.destination}</dd></div>
      <div><dt>Purpose</dt><dd>{preview.purpose}</dd></div>
      <div><dt>Minimized fields</dt><dd>{preview.minimizedFields.join(", ")}</dd></div>
      <div><dt>Confirmation expires</dt><dd>{new Date(preview.expiresAt).toLocaleString()}</dd></div></dl>
    <details><summary>Review outbound content</summary><pre>{outboundSummary}</pre></details>
    <p><strong>One request only.</strong> This confirmation token cannot be reused and becomes invalid if the content or destination changes. A payload-free local receipt records the destination, purpose, field names, hash, and outcome.</p>
    <div className="modal-actions"><button type="button" className="text-button" disabled={busy} onClick={onCancel}>Stay local-only</button>
      <button type="button" className="primary-button" disabled={busy} onClick={() => onConfirm(preview.confirmationToken)}>
        {busy ? "Sending…" : "Confirm this transmission"}</button></div>
  </section>;
}
