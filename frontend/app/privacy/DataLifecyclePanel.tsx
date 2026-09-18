"use client";

import { useEffect, useRef, useState } from "react";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://127.0.0.1:8080";

type Category = "EXPLICIT_USER_RECORDS" | "SENSITIVE_WORKSPACE_FILES" | "TRANSIENT_IMPORT_DATA"
  | "DERIVED_CONTEXT" | "AUDIT_AND_POLICY_METADATA" | "APPLICATION_REFERENCE_DATA";
type OperationType = "EXPORT" | "DELETE_CATEGORIES" | "DELETE_ALL";
type OperationStatus = "PREVIEWED" | "EXPORT_COMPLETED" | "DATABASE_DELETED" | "COMPLETED"
  | "PARTIAL_FILESYSTEM_FAILURE" | "FAILED" | "STALE" | "EXPIRED";

type Scope = { name: string; storageType: "DATABASE_TABLE" | "FILESYSTEM_PATH"; status: string;
  recordCount: number; fileCount: number; byteCount: number; skippedUnsafeEntryCount: number;
  includedInDeleteAll: boolean; scope: string };
type CategoryInventory = { category: Category; databaseRowCount: number; fileCount: number; byteCount: number;
  skippedUnsafeEntryCount: number; scopes: Scope[] };
type Boundary = { name: string; description: string; removableByApplication: boolean };
type Inventory = { categories: CategoryInventory[]; databaseTableCount: number;
  configuredFilesystemScopeCount: number; outsideApplicationControl: Boundary[] };
type PreviewScope = { name: string; storageType: string; recordCount: number; fileCount: number; byteCount: number };
type Preview = { operationId: string; operationType: OperationType; requestedCategories: Category[];
  effectiveCategories: Category[]; databaseRows: number; fileCount: number; byteCount: number;
  skippedUnsafeEntryCount: number; scopes: PreviewScope[]; confirmationPhrase: string | null;
  previewToken: string; expiresAt: string; retainedApplicationScopes: string[];
  outsideApplicationControl: Boundary[] };
type Receipt = { operationId: string; operationType: OperationType; categories: Category[];
  status: OperationStatus; plannedDatabaseRows: number; plannedFileCount: number; plannedByteCount: number;
  affectedDatabaseRows: number | null; deletedFileCount: number | null; failedFileCount: number | null;
  exportByteCount: number | null; createdAt: string; completedAt: string | null;
  outsideApplicationControl: string[] };

const categoryCopy: Record<Category, { label: string; description: string }> = {
  EXPLICIT_USER_RECORDS: { label: "Saved command-center records", description: "Openings, applications, contacts, outreach, preparation, reviews, profile, and preferences you deliberately saved." },
  SENSITIVE_WORKSPACE_FILES: { label: "Sensitive workspace files", description: "Resumes, notes, LinkedIn imports, preparation files, and company-target inputs inside the configured workspace." },
  TRANSIENT_IMPORT_DATA: { label: "Import and review staging", description: "Daily-opening imports, inbox candidates, duplicate review data, observations, and imported connection staging." },
  DERIVED_CONTEXT: { label: "Assistant-derived context", description: "Application-owned assistance runs and decisions. This is separate from Codex task history and memories." },
  AUDIT_AND_POLICY_METADATA: { label: "Policy and transmission metadata", description: "Privacy policy, payload-free cleanup receipts, and payload-free connected-transmission previews and receipts." },
  APPLICATION_REFERENCE_DATA: { label: "Application reference catalogs", description: "Non-personal skill catalogs used by the product. Delete all intentionally preserves these." },
};

const deletableCategories: Category[] = ["EXPLICIT_USER_RECORDS", "SENSITIVE_WORKSPACE_FILES",
  "TRANSIENT_IMPORT_DATA", "DERIVED_CONTEXT", "AUDIT_AND_POLICY_METADATA"];
const defaultExport = new Set<Category>(["EXPLICIT_USER_RECORDS", "SENSITIVE_WORKSPACE_FILES"]);

async function jsonRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) },
  });
  if (!response.ok) {
    let message = `Request failed (${response.status}).`;
    try {
      const body = await response.json() as { message?: string; detail?: string; error?: string };
      message = body.message ?? body.detail ?? body.error ?? message;
    } catch { /* Keep the status-only error. */ }
    throw new Error(message);
  }
  return response.json() as Promise<T>;
}

function bytes(value: number) {
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}

function toggle(set: Set<Category>, value: Category) {
  const next = new Set(set);
  if (next.has(value)) next.delete(value); else next.add(value);
  return next;
}

export function DataLifecyclePanel({ connected, onMessage }: {
  connected: boolean;
  onMessage?: (kind: "success" | "error", message: string) => void;
}) {
  const [inventory, setInventory] = useState<Inventory | null>(null);
  const [exportCategories, setExportCategories] = useState<Set<Category>>(() => new Set(defaultExport));
  const [deleteCategories, setDeleteCategories] = useState<Set<Category>>(() => new Set());
  const [preview, setPreview] = useState<Preview | null>(null);
  const [confirmation, setConfirmation] = useState("");
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadInventory = async () => {
    if (!connected) return;
    try { setInventory(await jsonRequest<Inventory>("/api/v1/privacy-policy/data-inventory")); setError(null); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Could not load the application data inventory."); }
  };
  useEffect(() => {
    if (!connected) return;
    let active = true;
    void jsonRequest<Inventory>("/api/v1/privacy-policy/data-inventory")
      .then(result => { if (active) { setInventory(result); setError(null); } })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : "Could not load the application data inventory."); });
    return () => { active = false; };
  }, [connected]);

  const requestPreview = async (operationType: OperationType, categories: Set<Category>) => {
    if (!connected || busy) return;
    if (operationType !== "DELETE_ALL" && categories.size === 0) {
      setError(operationType === "EXPORT" ? "Choose at least one category to export." : "Choose at least one category to delete.");
      return;
    }
    setBusy(true); setError(null); setReceipt(null);
    try {
      const result = await jsonRequest<Preview>("/api/v1/privacy-policy/data-lifecycle/preview", {
        method: "POST", headers: { "X-JSCC-Action": "preview-data-lifecycle" },
        body: JSON.stringify({ operationType, categories: [...categories] }),
      });
      setPreview(result); setConfirmation("");
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Could not create an exact-scope preview."); }
    finally { setBusy(false); }
  };

  const downloadExport = async () => {
    if (!preview || preview.operationType !== "EXPORT" || busy) return;
    setBusy(true); setError(null);
    try {
      const response = await fetch(`${API_BASE}/api/v1/privacy-policy/data-lifecycle/export`, {
        method: "POST", headers: { "Content-Type": "application/json", "X-JSCC-Action": "export-application-data" },
        body: JSON.stringify({ operationId: preview.operationId, previewToken: preview.previewToken }),
      });
      if (!response.ok) {
        let message = `Export failed (${response.status}).`;
        try { message = ((await response.json()) as { message?: string }).message ?? message; } catch { /* status is enough */ }
        throw new Error(message);
      }
      const blob = await response.blob();
      const disposition = response.headers.get("Content-Disposition") ?? "";
      const filename = disposition.match(/filename="([^"]+)"/)?.[1] ?? `job-search-command-center-export-${preview.operationId}.zip`;
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a"); link.href = url; link.download = filename; link.click();
      URL.revokeObjectURL(url);
      setPreview(null);
      onMessage?.("success", "Application-owned data export downloaded. Browser downloads and copies are outside app deletion controls.");
      await loadInventory();
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : "Could not download the data export.";
      setError(message); onMessage?.("error", message);
    } finally { setBusy(false); }
  };

  const executeDeletion = async () => {
    if (!preview || preview.operationType === "EXPORT" || confirmation !== preview.confirmationPhrase || busy) return;
    setBusy(true); setError(null);
    try {
      const result = await jsonRequest<Receipt>("/api/v1/privacy-policy/data-lifecycle/delete", {
        method: "POST", headers: { "X-JSCC-Action": "delete-application-data" },
        body: JSON.stringify({ operationId: preview.operationId, previewToken: preview.previewToken,
          confirmationPhrase: confirmation }),
      });
      setReceipt(result); setPreview(null); setConfirmation(""); await loadInventory();
      if (result.status === "PARTIAL_FILESYSTEM_FAILURE") {
        const message = "Database deletion completed, but one or more planned files could not be removed. Create a fresh preview and confirmation before retrying the remaining files.";
        setError(message); onMessage?.("error", message);
      } else {
        onMessage?.("success", "The confirmed application-owned data scope was deleted. A payload-free local receipt was retained.");
      }
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : "The deletion did not complete.";
      setError(message); onMessage?.("error", message);
    } finally { setBusy(false); }
  };

  return <section className="panel data-lifecycle-panel" aria-labelledby="data-lifecycle-title">
    <header className="data-lifecycle-heading"><div><p className="eyebrow">Application-owned data</p>
      <h2 id="data-lifecycle-title">Inventory, export, and deletion</h2>
      <p>Preview an exact local scope before downloading or deleting it. Reference catalogs and the payload-free operation journal are preserved.</p></div>
      <button type="button" className="text-button" disabled={!connected || busy} onClick={() => void loadInventory()}>Refresh inventory</button></header>

    {!connected ? <p className="privacy-error">Connect to the local API to use data lifecycle controls.</p>
      : error && !inventory ? <p className="privacy-error" role="alert">{error}</p>
        : !inventory ? <p className="privacy-loading" role="status">Loading application-owned data…</p>
          : <>
            <div className="data-lifecycle-summary" aria-label="Application data inventory summary">
              <article><strong>{inventory.categories.reduce((sum, item) => sum + item.databaseRowCount, 0)}</strong><span>database rows</span></article>
              <article><strong>{inventory.categories.reduce((sum, item) => sum + item.fileCount, 0)}</strong><span>workspace files</span></article>
              <article><strong>{bytes(inventory.categories.reduce((sum, item) => sum + item.byteCount, 0))}</strong><span>workspace content</span></article>
            </div>

            <section className="data-lifecycle-action" aria-labelledby="data-export-title"><header><div><span>Portable copy</span><h3 id="data-export-title">Export application-owned data</h3></div>
              <button type="button" className="secondary-button" disabled={busy || exportCategories.size === 0}
                onClick={() => void requestPreview("EXPORT", exportCategories)}>Preview export</button></header>
              <p>Defaults to deliberately saved records and sensitive workspace files. The ZIP and browser download history become copies outside application control.</p>
              <CategoryChoices categories={inventory.categories} selected={exportCategories} onChange={category => setExportCategories(toggle(exportCategories, category))} allowReference />
            </section>

            <section className="data-lifecycle-action danger" aria-labelledby="data-delete-title"><header><div><span>Destructive controls</span><h3 id="data-delete-title">Delete selected application data</h3></div>
              <button type="button" className="secondary-button danger-button" disabled={busy || deleteCategories.size === 0}
                onClick={() => void requestPreview("DELETE_CATEGORIES", deleteCategories)}>Preview selected deletion</button></header>
              <p>No deletion category is selected by default. Selecting saved records also includes dependent import and review staging so database relationships can be removed transactionally.</p>
              <CategoryChoices categories={inventory.categories.filter(item => deletableCategories.includes(item.category))}
                selected={deleteCategories} onChange={category => setDeleteCategories(toggle(deleteCategories, category))} />
              <div className="delete-all-row"><div><strong>Delete all application-owned user data</strong><small>Includes every deletable category above. Preserves canonical skill catalogs and the payload-free lifecycle journal.</small></div>
                <button type="button" className="danger-button" disabled={busy}
                  onClick={() => void requestPreview("DELETE_ALL", new Set())}>Preview delete all</button></div>
            </section>

            <details className="data-boundaries"><summary>What these controls cannot remove</summary><ul>
              {inventory.outsideApplicationControl.map(item => <li key={item.name}><strong>{item.name}</strong><span>{item.description}</span></li>)}</ul></details>
          </>}

    {error && inventory && <p className="form-error data-lifecycle-error" role="alert">{error}</p>}
    {receipt && <div className={`data-lifecycle-receipt ${receipt.status === "PARTIAL_FILESYSTEM_FAILURE" ? "partial" : ""}`} role="status">
      <strong>{receipt.status === "COMPLETED" ? "Deletion completed" : "Deletion needs a fresh preview"}</strong>
      <p>Preview revision {receipt.operationId.slice(0, 8)} · {receipt.affectedDatabaseRows ?? 0} database rows · {receipt.deletedFileCount ?? 0} files removed
        {receipt.failedFileCount ? ` · ${receipt.failedFileCount} files not removed` : ""}. This payload-free receipt contains counts and status, not deleted content or filesystem paths.</p>
    </div>}

    {preview && <DataLifecycleConfirmation preview={preview} confirmation={confirmation} busy={busy}
      onConfirmation={setConfirmation} onCancel={() => { setPreview(null); setConfirmation(""); }}
      onExport={() => void downloadExport()} onDelete={() => void executeDeletion()} />}
  </section>;
}

function CategoryChoices({ categories, selected, onChange, allowReference = false }: {
  categories: CategoryInventory[]; selected: Set<Category>; onChange: (category: Category) => void; allowReference?: boolean;
}) {
  return <div className="data-category-grid">{categories
    .filter(item => allowReference || item.category !== "APPLICATION_REFERENCE_DATA")
    .map(item => <label key={item.category} aria-label={categoryCopy[item.category].label}
      htmlFor={`data-category-${allowReference ? "export" : "delete"}-${item.category}`}
      className={selected.has(item.category) ? "selected" : ""}>
      <input id={`data-category-${allowReference ? "export" : "delete"}-${item.category}`} type="checkbox"
        checked={selected.has(item.category)} onChange={() => onChange(item.category)} />
      <span><strong>{categoryCopy[item.category].label}</strong><small>{categoryCopy[item.category].description}</small>
        <em>{item.databaseRowCount} rows · {item.fileCount} files · {bytes(item.byteCount)}</em></span></label>)}</div>;
}

function DataLifecycleConfirmation({ preview, confirmation, busy, onConfirmation, onCancel, onExport, onDelete }: {
  preview: Preview; confirmation: string; busy: boolean; onConfirmation: (value: string) => void;
  onCancel: () => void; onExport: () => void; onDelete: () => void;
}) {
  const deletion = preview.operationType !== "EXPORT";
  const dialog = useRef<HTMLElement>(null);
  useEffect(() => {
    const frame = window.requestAnimationFrame(() => dialog.current?.focus());
    return () => window.cancelAnimationFrame(frame);
  }, []);
  return <div className="modal-backdrop data-lifecycle-backdrop"><section ref={dialog} tabIndex={-1}
    className="modal data-lifecycle-modal" role="alertdialog"
    aria-modal="true" aria-labelledby="data-lifecycle-confirmation-title" aria-describedby="data-lifecycle-confirmation-description">
    <header><div><p className="eyebrow">Exact-scope preview</p><h2 id="data-lifecycle-confirmation-title">
      {deletion ? preview.operationType === "DELETE_ALL" ? "Confirm delete all" : "Confirm category deletion" : "Review your export"}</h2>
      <p id="data-lifecycle-confirmation-description">Preview revision {preview.operationId.slice(0, 8)} expires {new Date(preview.expiresAt).toLocaleString()} and authorizes only this unchanged scope.</p></div></header>
    <div className="data-lifecycle-modal-body">
      <div className="data-lifecycle-preview-totals"><article><strong>{preview.databaseRows}</strong><span>database rows</span></article>
        <article><strong>{preview.fileCount}</strong><span>workspace files</span></article><article><strong>{bytes(preview.byteCount)}</strong><span>file content</span></article></div>
      <div className="data-lifecycle-effective"><strong>Effective categories</strong><p>{preview.effectiveCategories.map(value => categoryCopy[value].label).join(" · ")}</p></div>
      <details><summary>Review exact storage scopes ({preview.scopes.length})</summary><ul>{preview.scopes.map(scope => <li key={`${scope.storageType}-${scope.name}`}>
        <span>{scope.name.replaceAll("_", "-")}</span><small>{scope.recordCount} rows · {scope.fileCount} files · {bytes(scope.byteCount)}</small></li>)}</ul></details>
      <div className="data-lifecycle-retained"><strong>Preserved application scopes</strong><p>{preview.retainedApplicationScopes.join(" · ")}</p></div>
      {deletion ? <label className="data-lifecycle-phrase" htmlFor="data-lifecycle-confirmation-phrase"><span>Type this exact phrase to continue</span><code>{preview.confirmationPhrase}</code>
        <input id="data-lifecycle-confirmation-phrase" value={confirmation} onChange={event => onConfirmation(event.target.value)} autoComplete="off" spellCheck={false} /></label>
        : <p className="data-lifecycle-export-note">The export contains sensitive content. Its browser download, copied files, backups, and synchronized-folder copies cannot be deleted by this application.</p>}
      <div className="modal-actions"><button type="button" className="text-button" disabled={busy} onClick={onCancel}>Cancel</button>
        {deletion ? <button type="button" className="danger-button" disabled={busy || confirmation !== preview.confirmationPhrase} onClick={onDelete}>
          {busy ? "Deleting…" : "Delete this exact scope"}</button>
          : <button type="button" className="primary-button" disabled={busy} onClick={onExport}>{busy ? "Preparing…" : "Download export ZIP"}</button>}</div>
      {deletion && <p className="data-lifecycle-retry-note">If database deletion succeeds but a file cannot be removed, the receipt reports only counts. Retrying requires a fresh preview, fresh token, and retyping the new exact phrase; an old preview never authorizes newly created files.</p>}
    </div>
  </section></div>;
}
