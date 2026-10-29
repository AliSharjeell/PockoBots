# MCP marketplace and sign-in

The marketplace includes factual connection definitions for all 30 vendors in
C:/AppsNew/openbot/marketplace/plugin-catalog/plugins plus Google Workspace.
The inspected local source has no .git metadata, so no commit identity could
be established. Its plugin catalog declares version v1 dated 2026-09-30.
OpenBot uses PolyForm Noncommercial; PockoBots does not copy its implementation,
prose, icons, prompts or skills. Vendor names, commands and endpoints are
connection metadata, with original PockoBots presentation text.

Included vendors: Aave, Airtable, Apple apps, Atlassian, Brave Search, Canva,
Cloudflare, Cobrain, Composio, Context7, Figma, Firecrawl, Hugging Face, Linear,
Neon, Netlify, Notion, Obsidian, Paper, PostHog, Prisma, Railway, Render, Resend,
Sentry, Stripe, Supabase, Todoist, Trello and Vercel. Google Workspace is added
as a separate entry. A catalog listing is not proof that an external service is
reachable, free, or compatible with this Android runtime. Test each connection.

Installing an entry saves a connection configuration. It reports configured,
not connected. Use Test to establish MCP tool connectivity. Local stdio servers
may download their package on first use. Apple apps requires macOS; Paper and
the catalog's Figma desktop endpoint need desktop software; Obsidian needs its
compatible REST API plugin. These four entries are marked external-host-required
and cannot be installed as pretend Android apps. Add an actually reachable
endpoint under custom connections if you have the required external host.

## MCP OAuth

HTTP entries with sign-in use the official MCP SDK's OAuth client, authorization
server/resource discovery, dynamic registration when supported, PKCE, issuer
binding, token refresh and a one-time random state parameter. Browser sign-in
redirects to the phone's loopback callback. Pending states expire after ten
minutes, cannot be replayed, and are lost on runtime restart; restart sign-in
if needed. A vendor may require pre-registration or disallow loopback clients;
a failed OAuth flow is reported instead of claiming account access.

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
