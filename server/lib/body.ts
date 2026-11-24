/** The request's JSON, or null if it isn't JSON or is larger than `maxBytes`. */
export async function readJson(request: Request, maxBytes: number): Promise<unknown> {
  if (Number(request.headers.get("content-length") ?? 0) > maxBytes) return null;
  try {
    const bytes = await request.arrayBuffer();
    return bytes.byteLength > maxBytes ? null : JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return null;
  }
}
