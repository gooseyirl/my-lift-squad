// Stamps the version badge and the ?v= cache-busting query on public/index.html
// before a deploy. This used to run in the gooseyirl.github.io Pages workflow;
// it moved here with the tool when it got its own domain.
//
// MINOR is 6 (not 5) purely so the version keeps climbing across the move: the
// patch number is this repo's commit count, which starts lower than the count
// the old repo had reached.
import { execSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";

const MAJOR_MINOR = "2.6";
const PATCH = execSync("git rev-list --count HEAD", { encoding: "utf8" }).trim();
const VERSION = `${MAJOR_MINOR}.${PATCH}`;

const FILE = new URL("./public/index.html", import.meta.url);
const before = readFileSync(FILE, "utf8");
const after = before
  .replace(/(id="appVersion">v)[^<]*/, `$1${VERSION}`)
  .replace(/(myliftsquad-import\.(?:css|js)\?v=)[^"']*/g, `$1${VERSION}`);

if (after === before) {
  console.log(`Version already at v${VERSION}`);
} else {
  writeFileSync(FILE, after);
  console.log(`Stamped version v${VERSION}`);
}
