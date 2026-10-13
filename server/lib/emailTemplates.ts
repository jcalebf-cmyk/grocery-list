import { SITE_URL, type Email } from "./email.js";

// The app's colors (Android Theme.kt): green primary, warm cream background.
const GREEN = "#1F6B3A";
const CREAM = "#FBF8F2";
const INK = "#1C1B17";
const BODY = "#4A473F";
const MUTED = "#7A766C";
const LINE = "#EBE6DC";
const FONT = `'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif`;

interface Recipient {
  email: string;
  displayName?: string | null;
}

export function confirmEmail(to: Recipient, link: string): Email {
  const subject = "Confirm your email for Groceries";
  const intro = `Welcome to Groceries! Please confirm that ${to.email} is your email address, so you can always get back into your account if you forget your password.`;
  const note = "Didn't sign up for Groceries? Someone may have typed your address by mistake. You can ignore this email.";
  return {
    to: to.email,
    subject,
    html: layout({
      subject,
      preheader: "One quick tap to confirm it's you.",
      heading: "Confirm your email",
      paragraphs: [greeting(to), intro],
      button: { label: "Confirm my email", url: link },
      note,
    }),
    text: [greeting(to), intro, `Confirm your email: ${link}`, note].join("\n\n"),
  };
}

export function resetPassword(to: Recipient, link: string): Email {
  const subject = "Reset your Groceries password";
  const intro = `We got a request to reset the password for your Groceries account (${to.email}). Tap the button to choose a new one.`;
  const note = "This link works once and expires soon. Didn't ask for this? You can ignore this email and your password will stay the same.";
  return {
    to: to.email,
    subject,
    html: layout({
      subject,
      preheader: "Choose a new password in a few seconds.",
      heading: "Reset your password",
      paragraphs: [greeting(to), intro],
      button: { label: "Choose a new password", url: link },
      note,
    }),
    text: [greeting(to), intro, `Choose a new password: ${link}`, note].join("\n\n"),
  };
}

function greeting(to: Recipient): string {
  const first = to.displayName?.trim().split(/\s+/)[0];
  return first ? `Hi ${first},` : "Hi there,";
}

interface Layout {
  subject: string;
  preheader: string;
  heading: string;
  paragraphs: string[];
  button: { label: string; url: string };
  note: string;
}

/**
 * Email HTML has to work in Gmail, Outlook and Apple Mail, so it's tables and inline styles. The <style>
 * block only adds extras (dark mode, phone padding) for the clients that support it.
 */
function layout(e: Layout): string {
  const url = escape(e.button.url);
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<meta name="supported-color-schemes" content="light dark">
<title>${escape(e.subject)}</title>
<link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600;700&display=swap" rel="stylesheet">
<style>
  @media (max-width: 600px) { .card { padding: 32px 24px 28px !important; } }
  @media (prefers-color-scheme: dark) {
    .bg { background: #14130F !important; }
    .card { background: #1F1E1A !important; border-color: #33312B !important; }
    .ink { color: #EDE8DD !important; }
    .body, .muted { color: #B8B2A5 !important; }
    .button { background: #8FD8A5 !important; }
    .button a { color: #00391C !important; }
    .link { color: #8FD8A5 !important; }
    .rule { border-color: #33312B !important; }
  }
</style>
</head>
<body class="bg" style="margin:0;padding:0;background:${CREAM};">
<div style="display:none;max-height:0;overflow:hidden;opacity:0;">${escape(e.preheader)}&#8199;&#847;&#8199;&#847;&#8199;&#847;&#8199;&#847;&#8199;&#847;&#8199;&#847;</div>
<table role="presentation" class="bg" width="100%" cellpadding="0" cellspacing="0" style="background:${CREAM};">
<tr><td align="center" style="padding:40px 16px;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:520px;">
    <tr><td align="center" style="padding-bottom:24px;">
      <table role="presentation" cellpadding="0" cellspacing="0"><tr>
        <td style="padding-right:10px;"><img src="${SITE_URL}/email/logo.png" width="36" height="36" alt="" style="display:block;border:0;border-radius:9px;"></td>
        <td class="ink" style="font-family:${FONT};font-size:20px;font-weight:700;color:${INK};letter-spacing:-0.2px;">Groceries</td>
      </tr></table>
    </td></tr>
    <tr><td class="card" style="background:#FFFFFF;border:1px solid ${LINE};border-radius:20px;padding:40px 40px 32px;">
      <h1 class="ink" style="margin:0 0 20px;font-family:${FONT};font-size:24px;line-height:32px;font-weight:700;color:${INK};letter-spacing:-0.3px;">${escape(e.heading)}</h1>
      ${e.paragraphs.map((p) => `<p class="body" style="margin:0 0 16px;font-family:${FONT};font-size:16px;line-height:24px;color:${BODY};">${escape(p)}</p>`).join("\n      ")}
      <table role="presentation" cellpadding="0" cellspacing="0" style="margin:28px 0 28px;"><tr>
        <td class="button" style="background:${GREEN};border-radius:999px;">
          <a href="${url}" style="display:inline-block;padding:14px 28px;font-family:${FONT};font-size:16px;line-height:20px;font-weight:600;color:#FFFFFF;text-decoration:none;border-radius:999px;">${escape(e.button.label)}</a>
        </td>
      </tr></table>
      <p class="muted" style="margin:0 0 24px;font-family:${FONT};font-size:13px;line-height:20px;color:${MUTED};">Button not working? Copy this link into your browser:<br><a class="link" href="${url}" style="color:${GREEN};word-break:break-all;">${url}</a></p>
      <p class="muted rule" style="margin:0;padding-top:20px;border-top:1px solid ${LINE};font-family:${FONT};font-size:13px;line-height:20px;color:${MUTED};">${escape(e.note)}</p>
    </td></tr>
    <tr><td align="center" class="muted" style="padding:24px 16px 0;font-family:${FONT};font-size:12px;line-height:18px;color:${MUTED};">Groceries · The shared grocery list for your household</td></tr>
  </table>
</td></tr>
</table>
</body>
</html>`;
}

function escape(s: string): string {
  return s.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
}
