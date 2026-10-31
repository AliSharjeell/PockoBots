import type { Connectors } from "./connectors.js";

/** Original presentation of factual vendor connection metadata. No OpenBot code or descriptions copied. */
export const marketplaceCatalog = [
  {
    "id": "aave",
    "name": "Aave",
    "category": "finance",
    "website": "https://aave.com",
    "description": "Explore lending and DeFi data.",
    "transport": "http",
    "url": "https://mcp.aave.com/mcp",
    "command": "",
    "args": [],
    "oauth": false,
    "fields": [],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "airtable",
    "name": "Airtable",
    "category": "data-analytics",
    "website": "https://www.airtable.com",
    "description": "Work with bases, tables and records.",
    "transport": "http",
    "url": "https://mcp.airtable.com/mcp",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Sign in in your browser, approve access, then return to PockoBots. Vendor account policies and client registration requirements still apply.",
    "setupUrl": "https://support.airtable.com/articles/9897799762-using-the-airtable-mcp-server"
  },
  {
    "id": "apple-apps",
    "name": "Apple apps",
    "category": "productivity",
    "website": "https://github.com/MrGo2/icloud-mcp",
    "description": "Use apps on a connected Apple host.",
    "transport": "stdio",
    "url": "",
    "command": "npx",
    "args": [
      "-y",
      "mcp-icloud@2.1.3"
    ],
    "oauth": false,
    "fields": [],
    "availability": "external-host-required",
    "setup": "This catalog entry depends on a compatible desktop application or host. It is not an Android app clone."
  },
  {
    "id": "atlassian",
    "name": "Atlassian",
    "category": "productivity",
    "website": "https://www.atlassian.com",
    "description": "Work with Jira issues and Confluence pages.",
    "transport": "http",
    "url": "https://mcp.atlassian.com/v2/mcp",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "brave-search",
    "name": "Brave Search",
    "category": "research",
    "website": "https://brave.com",
    "description": "Search the web.",
    "transport": "stdio",
    "url": "",
    "command": "npx",
    "args": [
      "-y",
      "@brave/brave-search-mcp-server"
    ],
    "oauth": false,
    "fields": [
      {
        "id": "token",
        "label": "API key",
        "env": "BRAVE_API_KEY"
      }
    ],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "canva",
    "name": "Canva",
    "category": "design",
    "website": "https://www.canva.com",
    "description": "Find and work with your designs.",
    "transport": "http",
    "url": "https://mcp.canva.com/mcp",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "cloudflare",
    "name": "Cloudflare",
    "category": "coding",
    "website": "https://www.cloudflare.com",
    "description": "Manage your Cloudflare resources.",
    "transport": "http",
    "url": "https://mcp.cloudflare.com/mcp",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "cobrain",
    "name": "Cobrain",
    "category": "productivity",
    "website": "https://cobrain.space",
    "description": "Connect your Cobrain workspace.",
    "transport": "http",
    "url": "https://cobrain.space/mcp",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "composio",
    "name": "Composio",
    "category": "automation",
    "website": "https://composio.dev",
    "description": "Connect apps through your Composio account.",
    "transport": "http",
    "url": "https://connect.composio.dev/mcp",
    "command": "",
    "args": [],
    "oauth": false,
    "fields": [
      {
        "id": "api-key",
        "label": "Composio consumer API key",
        "header": "x-consumer-api-key"
      }
    ],
    "availability": "setup-required",
    "setup": "Sign into Composio For You and connect your apps there. Paste its consumer API key here. Composio stores and refreshes those app credentials on its service.",
    "setupUrl": "https://docs.composio.dev/kb/guide/consumer-project-boundaries-and-auth-selection"
  },
  {
    "id": "context7",
    "name": "Context7",
    "category": "research",
    "website": "https://context7.com",
    "description": "Find current library documentation.",
    "transport": "http",
    "url": "https://mcp.context7.com/mcp",
    "command": "",
    "args": [],
    "oauth": false,
    "fields": [],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "figma",
    "name": "Figma",
    "category": "design",
    "website": "https://www.figma.com",
    "description": "Read design context from Figma.",
    "transport": "http",
    "url": "https://mcp.figma.com/mcp",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Sign in with Figma. Figma currently restricts access to supported clients; if this app is not approved, join its client waitlist or use a supported external host.",
    "setupUrl": "https://developers.figma.com/docs/figma-mcp-server/remote-server-installation/"
  },
  {
    "id": "firecrawl",
    "name": "Firecrawl",
    "category": "research",
    "website": "https://www.firecrawl.dev",
    "description": "Search and extract website content.",
    "transport": "stdio",
    "url": "",
    "command": "npx",
    "args": [
      "-y",
      "firecrawl-mcp"
    ],
    "oauth": false,
    "fields": [
      {
        "id": "token",
        "label": "API key",
        "env": "FIRECRAWL_API_KEY",
        "prefix": ""
      }
    ],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "huggingface",
    "name": "Hugging Face",
    "category": "research",
    "website": "https://huggingface.co",
    "description": "Find models, datasets and Spaces.",
    "transport": "http",
    "url": "https://huggingface.co/mcp",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Sign in in your browser, approve access, then return to PockoBots. Vendor account policies and client registration requirements still apply.",
    "setupUrl": "https://huggingface.co/docs/hub/en/agents-mcp"
  },
  {
    "id": "linear",
    "name": "Linear",
    "category": "coding",
    "website": "https://linear.app",
    "description": "Manage issues and projects.",
    "transport": "http",
    "url": "https://mcp.linear.app/mcp",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "neon",
    "name": "Neon",
    "category": "coding",
    "website": "https://neon.com",
    "description": "Work with Postgres projects.",
    "transport": "http",
    "url": "https://mcp.neon.tech/mcp",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "netlify",
    "name": "Netlify",
    "category": "coding",
    "website": "https://www.netlify.com",
    "description": "Manage sites and deployments.",
    "transport": "http",
    "url": "https://netlify-mcp.netlify.app/mcp",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "notion",
    "name": "Notion",
    "category": "productivity",
    "website": "https://www.notion.so",
    "description": "Search and work with pages.",
    "transport": "http",
    "url": "https://mcp.notion.com/mcp",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "obsidian",
    "name": "Obsidian",
    "category": "productivity",
    "website": "https://obsidian.md",
    "description": "Read and edit notes on a connected host.",
    "transport": "stdio",
    "url": "",
    "command": "npx",
    "args": [
      "-y",
      "obsidian-mcp-server@3.7.2"
    ],
    "oauth": false,
    "fields": [
      {
        "id": "token",
        "label": "API key",
        "env": "OBSIDIAN_API_KEY"
      }
    ],
    "availability": "external-host-required",
    "setup": "This catalog entry depends on a compatible desktop application or host. It is not an Android app clone."
  },
  {
    "id": "paper",
    "name": "Paper",
    "category": "design",
    "website": "https://paper.design",
    "description": "Work with designs on a connected desktop.",
    "transport": "stdio",
    "url": "",
    "command": "~/.paper/bin/paper",
    "args": [
      "mcp"
    ],
    "oauth": false,
    "fields": [],
    "availability": "external-host-required",
    "setup": "This catalog entry depends on a compatible desktop application or host. It is not an Android app clone."
  },
  {
    "id": "posthog",
    "name": "PostHog",
    "category": "data-analytics",
    "website": "https://posthog.com",
    "description": "Explore analytics, flags and events.",
    "transport": "http",
    "url": "https://mcp.posthog.com/mcp",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Sign in in your browser, approve access, then return to PockoBots. Vendor account policies and client registration requirements still apply.",
    "setupUrl": "https://posthog.com/docs/model-context-protocol/vscode"
  },
  {
    "id": "prisma",
    "name": "Prisma",
    "category": "coding",
    "website": "https://www.prisma.io",
    "description": "Work with databases.",
    "transport": "http",
    "url": "https://mcp.prisma.io/mcp",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "railway",
    "name": "Railway",
    "category": "coding",
    "website": "https://railway.com",
    "description": "Manage projects and deployments.",
    "transport": "http",
    "url": "https://mcp.railway.com",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "render",
    "name": "Render",
    "category": "coding",
    "website": "https://render.com",
    "description": "Manage services and workspaces.",
    "transport": "http",
    "url": "https://mcp.render.com/mcp",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Try browser sign-in. If Render requires a registered client, open Configure and use a Render API key instead. Select your Render workspace when asking your bot to use it.",
    "setupUrl": "https://render.com/docs/mcp-server"
  },
  {
    "id": "resend",
    "name": "Resend",
    "category": "automation",
    "website": "https://resend.com",
    "description": "Work with email and contacts.",
    "transport": "http",
    "url": "https://mcp.resend.com/mcp",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Sign in in your browser, approve access, then return to PockoBots. Vendor account policies and client registration requirements still apply.",
    "setupUrl": "https://github.com/resend/resend-mcp"
  },
  {
    "id": "sentry",
    "name": "Sentry",
    "category": "coding",
    "website": "https://sentry.io",
    "description": "Investigate errors and issues.",
    "transport": "http",
    "url": "https://mcp.sentry.dev/mcp",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "stripe",
    "name": "Stripe",
    "category": "finance",
    "website": "https://stripe.com",
    "description": "Work with payments and customers.",
    "transport": "http",
    "url": "https://mcp.stripe.com",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "supabase",
    "name": "Supabase",
    "category": "coding",
    "website": "https://supabase.com",
    "description": "Manage projects and database resources.",
    "transport": "http",
    "url": "https://mcp.supabase.com/mcp",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "todoist",
    "name": "Todoist",
    "category": "productivity",
    "website": "https://www.todoist.com",
    "description": "Manage tasks and projects.",
    "transport": "http",
    "url": "https://ai.todoist.net/mcp",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "trello",
    "name": "Trello",
    "category": "productivity",
    "website": "https://trello.com",
    "description": "Work with boards, lists and cards.",
    "transport": "http",
    "url": "https://mcp.trello.com/v1",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "vercel",
    "name": "Vercel",
    "category": "coding",
    "website": "https://vercel.com",
    "description": "Manage projects and deployments.",
    "transport": "http",
    "url": "https://mcp.vercel.com",
    "command": "",
    "args": [],
    "oauth": true,
    "fields": [],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "google-workspace",
    "name": "Google Workspace",
    "category": "productivity",
    "website": "https://github.com/taylorwilsdon/google_workspace_mcp",
    "description": "Work with Gmail, Drive and Calendar.",
    "transport": "stdio",
    "url": "",
    "command": "uvx",
    "args": [
      "workspace-mcp",
      "--tool-tier",
      "core"
    ],
    "oauth": false,
    "fields": [
      {
        "id": "google-client-id",
        "label": "Google OAuth client ID",
        "env": "GOOGLE_OAUTH_CLIENT_ID"
      },
      {
        "id": "google-client-secret",
        "label": "Google OAuth client secret",
        "env": "GOOGLE_OAUTH_CLIENT_SECRET"
      }
    ],
    "availability": "setup-required",
    "setup": "Install uv first in the main terminal, then ensure uvx is in /usr/local/bin (the packaged runtime PATH). Create a Google Cloud OAuth client, enable the APIs, and add your account as a test user. Enter client credentials here. The server returns a Google sign-in link through its authentication tool; open the link in your phone browser and approve scopes. This is a third-party MIT Workspace MCP server, not a Google-supported product.",
    "setupUrl": "https://workspacemcp.com/quick-start"
  }
] as const;

export function listMarketplace(connectors: Connectors) {
 const configured=connectors.list();
 return marketplaceCatalog.map(entry=>({...entry,connection:configured.find(c=>c.marketplaceId===entry.id),status:configured.some(c=>c.marketplaceId===entry.id)?"configured":"not_configured"}));
}
export async function installMarketplace(connectors: Connectors,id: string,input: any={}) {
 const entry=marketplaceCatalog.find(e=>e.id===id);if(!entry)throw Error("Marketplace item not found");
 if(entry.availability==="external-host-required")throw Error("This integration needs a compatible desktop host. Add its reachable MCP endpoint as a custom connection.");
 const existing=connectors.list().find(c=>c.marketplaceId===entry.id);
 const secrets=input.credentials || {};if(typeof secrets!=="object"||Array.isArray(secrets))throw Error("Invalid connection credentials");
 const fields=entry.fields as readonly {id:string;label:string;env?:string;header?:string;url?:boolean;prefix?:string}[];
 const env:Record<string,string>={},headers:Record<string,string>={};let url:string=fields.some(f=>f.url)&&existing?existing.url:entry.url;
 const sameTarget=existing?.transport===entry.transport&&(entry.transport==="http"?existing.url===url:existing.command===entry.command);
 for(const field of fields) {
  const value=secrets[field.id];
  if(value===undefined||value===""){
   if(sameTarget&&(field.url||field.env&&existing?.envKeys.includes(field.env)||field.header&&existing?.headerKeys.includes(field.header)))continue;
   throw Error("Enter "+field.label);
  }
  if(typeof value!=="string"||!value.trim())throw Error("Enter "+field.label);
  if(field.url)url=value;else if(field.env)env[field.env]=value;else if(field.header)headers[field.header]=(field.prefix||"")+value;
 }
 const useApiKey=input.useApiKey===true;
 if(useApiKey&&(!entry.oauth||entry.transport!=="http"))throw Error("This app does not support an alternate bearer API key");
 if(useApiKey&&!(typeof input.token==="string"&&input.token.trim())&&!(sameTarget&&existing?.hasToken))throw Error("Enter an API key");
 const connection=await connectors.save({id:existing?.id,marketplaceId:entry.id,name:entry.name,transport:entry.transport,url,command:entry.command,args:[...entry.args],env,headers,preserveSecrets:true,token:useApiKey?input.token:undefined,oauth:entry.oauth&&!useApiKey});
 return {connection,status:"configured",setup:entry.setup,needsSignIn:connection.oauth&&connection.authStatus!=="authorized"};
}
