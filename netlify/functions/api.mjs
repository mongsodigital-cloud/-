import { getStore } from "@netlify/blobs";

function json(data, status = 200) {
  return Response.json(data, { status });
}

function tokenMatches(req) {
  const expected = Netlify.env.get("DEVICE_TOKEN")?.trim() ?? "";
  const actual = req.headers.get("x-device-token")?.trim() ?? "";
  return Boolean(expected) && actual.length === expected.length && actual === expected;
}

export default async (req) => {
  const url = new URL(req.url);

  if (url.pathname === "/api/health" && req.method === "GET") {
    const token = Netlify.env.get("DEVICE_TOKEN")?.trim() ?? "";
    return json({
      ok: true,
      tokenConfigured: Boolean(token),
      tokenLength: token.length,
      service: "android-remote-lab",
      time: new Date().toISOString()
    });
  }

  if (!tokenMatches(req)) return json({ error: "unauthorized" }, 401);

  const store = getStore("android-remote-lab", { consistency: "strong" });

  if (url.pathname === "/api/command") {
    if (req.method === "POST") {
      const body = await req.json().catch(() => ({}));
      if (body?.command !== "OPEN_WHATSAPP") {
        return json({ error: "unsupported command" }, 400);
      }
      const command = {
        id: crypto.randomUUID(),
        command: "OPEN_WHATSAPP",
        createdAt: new Date().toISOString()
      };
      await store.setJSON("pending", command);
      return json({ ok: true, commandId: command.id });
    }

    if (req.method === "GET") {
      const command = await store.get("pending", { type: "json" });
      if (command) await store.delete("pending");
      return json({ command: command ?? null });
    }
  }

  if (url.pathname === "/api/result" && req.method === "POST") {
    const body = await req.json().catch(() => ({}));
    await store.setJSON("last-result", {
      ...body,
      receivedAt: new Date().toISOString()
    });
    return json({ ok: true });
  }

  return json({ error: "not_found" }, 404);
};

export const config = { path: "/api/*" };
