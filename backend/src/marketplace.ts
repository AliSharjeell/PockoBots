import type { Connectors } from "./connectors.js";

/** Original presentation of factual vendor connection metadata. No OpenBot code or descriptions copied. */
export const marketplaceCatalog = [
  {
    "id": "aave",
    "name": "Aave",
    "category": "data-analytics",
    "website": "https://aave.com",
    "description": "Connect Aave tools through MCP.",
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
    "description": "Connect Airtable tools through MCP.",
    "transport": "stdio",
    "url": "",
    "command": "npx",
    "args": [
      "-y",
      "airtable-mcp-server"
    ],
    "oauth": false,
    "fields": [
      {
        "id": "token",
        "label": "API key",
        "env": "AIRTABLE_API_KEY"
      }
    ],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "apple-apps",
    "name": "Apple apps",
    "category": "productivity",
    "website": "https://github.com/MrGo2/icloud-mcp",
    "description": "Connect Apple apps tools through MCP.",
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
    "description": "Connect Atlassian tools through MCP.",
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
    "description": "Connect Brave Search tools through MCP.",
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
    "description": "Connect Canva tools through MCP.",
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
    "description": "Connect Cloudflare tools through MCP.",
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
    "description": "Connect Cobrain tools through MCP.",
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
    "description": "Connect Composio tools through MCP.",
    "transport": "http",
    "url": "https://composio.dev/",
    "command": "",
    "args": [],
    "oauth": false,
    "fields": [
      {
        "id": "url",
        "label": "MCP URL",
        "url": true
      },
      {
        "id": "api-key",
        "label": "API key",
        "header": "x-consumer-api-key"
      }
    ],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "context7",
    "name": "Context7",
    "category": "research",
    "website": "https://context7.com",
    "description": "Connect Context7 tools through MCP.",
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
    "description": "Connect Figma tools through MCP.",
    "transport": "http",
    "url": "http://127.0.0.1:3845/mcp",
    "command": "",
    "args": [],
    "oauth": false,
    "fields": [],
    "availability": "external-host-required",
    "setup": "This catalog entry depends on a compatible desktop application or host. It is not an Android app clone."
  },
  {
    "id": "firecrawl",
    "name": "Firecrawl",
    "category": "research",
    "website": "https://www.firecrawl.dev",
    "description": "Connect Firecrawl tools through MCP.",
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
    "description": "Connect Hugging Face tools through MCP.",
    "transport": "http",
    "url": "https://huggingface.co/mcp",
    "command": "",
    "args": [],
    "oauth": false,
    "fields": [],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "linear",
    "name": "Linear",
    "category": "coding",
    "website": "https://linear.app",
    "description": "Connect Linear tools through MCP.",
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
    "description": "Connect Neon tools through MCP.",
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
    "description": "Connect Netlify tools through MCP.",
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
    "description": "Connect Notion tools through MCP.",
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
    "description": "Connect Obsidian tools through MCP.",
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
    "description": "Connect Paper tools through MCP.",
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
    "description": "Connect PostHog tools through MCP.",
    "transport": "http",
    "url": "https://mcp.posthog.com/mcp",
    "command": "",
    "args": [],
    "oauth": false,
    "fields": [
      {
        "id": "token",
        "label": "Personal API key",
        "header": "Authorization",
        "prefix": "Bearer "
      }
    ],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "prisma",
    "name": "Prisma",
    "category": "coding",
    "website": "https://www.prisma.io",
    "description": "Connect Prisma tools through MCP.",
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
    "description": "Connect Railway tools through MCP.",
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
    "description": "Connect Render tools through MCP.",
    "transport": "http",
    "url": "https://mcp.render.com/mcp",
    "command": "",
    "args": [],
    "oauth": false,
    "fields": [
      {
        "id": "token",
        "label": "API key",
        "header": "Authorization",
        "prefix": "Bearer "
      }
    ],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "resend",
    "name": "Resend",
    "category": "automation",
    "website": "https://resend.com",
    "description": "Connect Resend tools through MCP.",
    "transport": "stdio",
    "url": "",
    "command": "npx",
    "args": [
      "-y",
      "resend-mcp"
    ],
    "oauth": false,
    "fields": [
      {
        "id": "token",
        "label": "API key",
        "env": "RESEND_API_KEY",
        "prefix": ""
      }
    ],
    "availability": "setup-required",
    "setup": "Add your credentials if required, then test this connection. Local servers may download packages when first started."
  },
  {
    "id": "sentry",
    "name": "Sentry",
    "category": "coding",
    "website": "https://sentry.io",
    "description": "Connect Sentry tools through MCP.",
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
    "category": "other",
    "website": "https://stripe.com",
    "description": "Connect Stripe tools through MCP.",
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
    "description": "Connect Supabase tools through MCP.",
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
    "description": "Connect Todoist tools through MCP.",
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
    "description": "Connect Trello tools through MCP.",
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
    "description": "Connect Vercel tools through MCP.",
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
    "description": "Google Gmail, Drive and Calendar tools using your own Google OAuth client.",
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
 const secrets=input.credentials || {};if(typeof secrets!=="object"||Array.isArray(secrets))throw Error("Invalid connection credentials");
 const env:Record<string,string>={},headers:Record<string,string>={};let url:string=entry.url;
 for(const field of entry.fields as readonly {id:string;label:string;env?:string;header?:string;url?:boolean;prefix?:string}[]) {
  const value=secrets[field.id];if(typeof value!=="string"||!value.trim())throw Error("Enter "+field.label);
  if(field.url)url=value;else if(field.env)env[field.env]=value;else if(field.header)headers[field.header]=(field.prefix||"")+value;
 }
 const existing=connectors.list().find(c=>c.marketplaceId===entry.id);
 const connection=await connectors.save({id:existing?.id,marketplaceId:entry.id,name:entry.name,transport:entry.transport,url,command:entry.command,args:[...entry.args],env,headers,oauth:entry.oauth});
 return {connection,status:"configured",setup:entry.setup,needsSignIn:entry.oauth};
}
