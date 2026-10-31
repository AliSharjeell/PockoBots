# MCP marketplace and sign-in

The marketplace includes factual connection definitions for all 30 vendors in
C:/AppsNew/openbot/marketplace/plugin-catalog/plugins plus Google Workspace and GitHub.
The inspected local source has no .git metadata, so no commit identity could
be established. Its plugin catalog declares version v1 dated 2026-09-30.
OpenBot uses PolyForm Noncommercial; PockoBots does not copy its implementation,
prose, icons, prompts or skills. Vendor names, commands and endpoints are
connection metadata, with original PockoBots presentation text.

Included vendors: Aave, Airtable, Apple apps, Atlassian, Brave Search, Canva,
Cloudflare, Cobrain, Composio, Context7, Figma, Firecrawl, Hugging Face, Linear,
Neon, Netlify, Notion, Obsidian, Paper, PostHog, Prisma, Railway, Render, Resend,
Sentry, Stripe, Supabase, Todoist, Trello and Vercel. Google Workspace is added
with GitHub as additional entries. A catalog listing is not proof that an external service is
reachable, free, or compatible with this Android runtime. Test each connection.

The native directory starts with a four-column Essentials grid, then groups flat app rows under category headings. App logos
are bundled, with provenance in third_party/marketplace-icons/sources.json.
There are no category badges or per-app cards. Tap Add to connect; a saved entry
shows Added and a three-dot menu for sign-in, configuration, connection checks,
help or removal. Added means a configuration exists, not that account access
has been granted. The separate sign-in caption reflects authorization state.

Installing an entry saves a connection configuration. OAuth entries immediately
start browser authorization, while key-based entries first request credentials.
The app checks tool connectivity after a key-based setup; the OAuth callback
checks it after authorization and offers Return to PockoBots. Local stdio servers
may download their package on first use. Apple apps requires macOS; Paper needs desktop software; Obsidian needs its
compatible REST API plugin. These three entries are marked external-host-required
and cannot be installed as pretend Android apps. Add an actually reachable
endpoint under custom connections if you have the required external host.

## MCP OAuth

HTTP entries with sign-in use the official MCP SDK's OAuth client, authorization
server/resource discovery, dynamic registration when supported, PKCE, issuer
binding, token refresh and a one-time random state parameter. Browser sign-in
redirects to the phone's loopback callback. The return link navigates to pockobots://oauth/complete; it contains no tokens
or authorization codes. Pending states expire after ten
minutes, cannot be replayed, and are lost on runtime restart; restart sign-in
if needed. A vendor may require pre-registration or disallow loopback clients;
a failed OAuth flow is reported instead of claiming account access. The
configuration sheets keep saved credentials when fields are left blank.
HTTP OAuth entries also offer an optional bearer API-key fallback for vendors
that support it. Provider-specific headers still use their named fields.

Tokens, refresh tokens, verifier values, custom auth headers and stdio environment
values are saved only in private app state and omitted from public connector
state. Diagnostics and tool output redact those known values. Editing a
connection's destination drops inherited credentials. The app's existing
private storage does not add encryption at rest. Removing a connection removes
its stored credentials; vendor-side account revocation remains with that vendor.

## Google Workspace

This entry uses the MIT third-party server
[taylorwilsdon/google_workspace_mcp](https://github.com/taylorwilsdon/google_workspace_mcp),
not a Google-supported MCP product. It launches `uvx workspace-mcp --tool-tier core`.
Current Google Workspace CLI documentation does not establish a `gws mcp` command,
so the app does not advertise one.

1. Install [uv from Astral](https://docs.astral.sh/uv/getting-started/installation/)
   in the main terminal. Ensure uvx is available in /usr/local/bin, which is on
   the packaged runtime PATH. Installation and Python package downloads use the
   network. A missing uvx command is a setup error, not a connected integration.
2. Follow the server's [OAuth quick start](https://workspacemcp.com/quick-start).
   Use your own Google Cloud project and OAuth client, enable the desired APIs,
   configure allowed redirect URIs, and add your account as a test user when
   applicable. PockoBots cannot create or verify Google's consent configuration
   without your Google account and project.
3. Supply GOOGLE_OAUTH_CLIENT_ID and GOOGLE_OAUTH_CLIENT_SECRET in the marketplace
   dialog. These become private stdio environment values, not command arguments.
4. Test the MCP connection, then select its tools for your bot. The Workspace
   server's authentication tool returns the Google consent URL. Open it in the
   phone browser and approve the requested scopes. This server handles its own
   Google OAuth callback and credential cache.

The generic PockoBots MCP OAuth button is for HTTP MCP protocol OAuth. Google
Workspace's local stdio server manages Google OAuth itself. Merely supplying a
client ID/secret does not mean that a Google user has signed in. Live Google
consent and paid/vendor account connectivity were not exercised by local tests.

## Server integration

Construct Connectors with the configured loopback callback URL. Register
registerMarketplaceCallback before bearer authentication; its one-time state
and exact loopback Host check authorize only the OAuth callback. Register
registerMarketplaceApi after bearer authentication. Pass the sessions/mobile
busy guard so connection changes do not interrupt an active agent.

Endpoints:

- GET /api/marketplace returns catalog entries and configured status.
- POST /api/marketplace/:id/install accepts {credentials:{fieldId:value}}.
- POST /api/connectors/:id/oauth/start returns authorizationUrl when consent is needed.
- GET /api/oauth/mcp/callback consumes state and exchanges the authorization code.

Tests use a real loopback authorization server fixture to verify PKCE, private
token persistence, state mismatch/replay rejection and secret redaction. No
production account is represented as connected by these tests.

## Browser sign-in coverage and reference research - 2026-10-31

The catalogue now marks 22 of 32 entries for direct MCP OAuth, plus GitHub device authorization. This is protocol
configuration, not a claim that all 22 accounts or vendor client policies have
been exercised. No production account consent was automated during development.
Airtable, Figma, PostHog, Resend, Render and Hugging Face now prefer their remote
services instead of mandatory local keys or desktop-only setup. Sources:

- [Airtable hosted MCP and OAuth](https://www.airtable.com/platform/mcp)
- [Figma remote installation and supported-client restrictions](https://developers.figma.com/docs/figma-mcp-server/remote-server-installation/)
- [PostHog sign-in and region routing](https://posthog.com/docs/model-context-protocol/vscode)
- [Resend's official open-source server and remote OAuth endpoint](https://github.com/resend/resend-mcp)
- [Render OAuth and API-key options](https://render.com/docs/mcp-server)
- [Hugging Face server setup](https://huggingface.co/docs/hub/en/agents-mcp)

Public OAuth discovery was read without signing in for Airtable, Figma, PostHog
and Hugging Face. Each advertised an authorization endpoint and dynamic client
registration. Hugging Face's metadata was available at
https://huggingface.co/.well-known/oauth-authorization-server. This is not a
successful consent test. Figma restricts supported clients; Render may require
client registration. The UI exposes errors and a supported API-key alternative
rather than using another application's client ID.

The integration-platform review covered [Composio managed authentication](https://docs.composio.dev/docs/authentication)
and [Nango's auth/integration runtime](https://github.com/NangoHQ/nango).
They offer useful account-connection UIs and token lifecycle handling, but add
a hosted or separately deployed broker and its account/configuration. PockoBots
therefore uses the existing open-source MCP SDK for direct PKCE browser login,
discovery, registration and refresh. No mandatory broker backend was added.
Composio remains an optional marketplace connector: use its consumer endpoint
and your own consumer API key, with upstream apps connected in Composio For You.
Its credentials and app actions are handled by Composio's service, as described
in [its consumer-project guide](https://docs.composio.dev/kb/guide/consumer-project-boundaries-and-auth-selection).

Google Workspace's local connector still requires your own Google OAuth client.
An authentication library cannot supply an approved Google application identity
by itself. Services that only accept API keys, require an approved client, or
need another desktop host retain a guided configuration flow.

Five scoped backend checks cover catalogue coverage, PKCE/state replay guards,
private token storage, blank-field credential retention, bearer-key fallback,
authenticated mutation routes and the token-free app return page. They use
local fixtures, not real vendor accounts. Visual review is left to the user.

## GitHub device sign-in

GitHub does not support dynamic client registration for its hosted MCP server.
The GitHub entry therefore uses the official GitHub CLI's browser/device flow,
not the generic MCP registration flow. Its consent page identifies **GitHub CLI**.
The native sheet shows the one-time code and a Copy code & open GitHub button.
After approval, the app checks MCP connectivity. Access and organization policy
are still determined by GitHub; a sign-in does not bypass those policies.

On first use, the private Linux runtime downloads GitHub CLI **2.103.3** from
[its official release](https://github.com/cli/cli/releases/tag/v2.103.3), supporting
Linux amd64 and arm64. Downloads are limited to 64 MiB and checked against the
[published SHA-256 checksums](https://github.com/cli/cli/releases/download/v2.103.3/gh_2.103.3_checksums.txt):

- amd64: `4c806978ffce1cec70853302a56d70ac6da4f19bae7adca4e1a8ab00633dd91d`
- arm64: `ee32df2323c8847f5fac6cb6b4b69ca75828e9329a8f22f677021f7b3306ce63`

Each attempt uses an isolated private CLI account directory, excludes inherited
GitHub tokens, expires after 15 minutes, and is cancelled when dismissed or the
connection changes. Temporary account files are removed after the access token
is saved to that exact GitHub connection's private state. The owner-only polling
endpoint exposes status/code, never the token or raw CLI output. Reauthorize if
the token is revoked. This does not sign in the global terminal's gh account.

Implementation references: [GitHub host integration](https://github.com/github/github-mcp-server/blob/main/docs/host-integration.md)
and [gh auth login](https://cli.github.com/manual/gh_auth_login).
Tests exercise real child-process device-code parsing, private token transfer,
cancellation and cleanup using a local fake CLI. They do not authorize a real
GitHub account. GET /api/connectors/:id/oauth/status and POST to the same base
path /cancel require the normal owner bearer authentication.
