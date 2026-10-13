/** The app's website: hosts the page that email links open, and the logo the emails show. */
export const SITE_URL = process.env.SITE_URL ?? "https://householdgroceries.app";
const FROM = `Groceries <hello@${new URL(SITE_URL).hostname}>`;

export interface Email {
  to: string;
  subject: string;
  html: string;
  text: string;
}

/** Sends through Resend (RESEND_API_KEY). The sending domain must be verified in Resend first. */
export async function sendEmail(email: Email): Promise<void> {
  const key = process.env.RESEND_API_KEY;
  if (!key) throw new Error("RESEND_API_KEY isn't set");
  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
    body: JSON.stringify({ from: FROM, ...email }),
  });
  if (!response.ok) throw new Error(`Resend returned ${response.status}: ${await response.text()}`);
}

/**
 * Firebase's links open its own plain page; point them at ours instead, keeping the one-time code
 * (oobCode), mode, and API key the page needs to finish the job.
 */
export function toOurPage(firebaseLink: string): string {
  return `${SITE_URL}/account?${new URL(firebaseLink).searchParams}`;
}
