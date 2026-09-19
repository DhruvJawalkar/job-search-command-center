import path from "node:path";

import { startProdServer } from "vinext/server/prod-server";

const port = Number.parseInt(process.env.PORT ?? "3000", 10);
const host = process.env.PORTAL_HOST ?? "0.0.0.0";

await startProdServer({
  port,
  host,
  outDir: path.resolve("dist"),
});
