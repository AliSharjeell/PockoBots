package dev.pocket.agent;
final class MarketplaceIcons {
    static int resource(String id) {
        switch(id) {
            case "github": return R.drawable.brand_github;
            case "aave": return R.drawable.brand_aave;
            case "airtable": return R.drawable.brand_airtable;
            case "apple-apps": return R.drawable.brand_apple_apps;
            case "atlassian": return R.drawable.brand_atlassian;
            case "brave-search": return R.drawable.brand_brave_search;
            case "canva": return R.drawable.brand_canva;
            case "cloudflare": return R.drawable.brand_cloudflare;
            case "cobrain": return R.drawable.brand_cobrain;
            case "composio": return R.drawable.brand_composio;
            case "context7": return R.drawable.brand_context7;
            case "figma": return R.drawable.brand_figma;
            case "firecrawl": return R.drawable.brand_firecrawl;
            case "google-workspace": return R.drawable.brand_google_workspace;
            case "huggingface": return R.drawable.brand_huggingface;
            case "linear": return R.drawable.brand_linear;
            case "neon": return R.drawable.brand_neon;
            case "netlify": return R.drawable.brand_netlify;
            case "notion": return R.drawable.brand_notion;
            case "obsidian": return R.drawable.brand_obsidian;
            case "paper": return R.drawable.brand_paper;
            case "posthog": return R.drawable.brand_posthog;
            case "prisma": return R.drawable.brand_prisma;
            case "railway": return R.drawable.brand_railway;
            case "render": return R.drawable.brand_render;
            case "resend": return R.drawable.brand_resend;
            case "sentry": return R.drawable.brand_sentry;
            case "stripe": return R.drawable.brand_stripe;
            case "supabase": return R.drawable.brand_supabase;
            case "todoist": return R.drawable.brand_todoist;
            case "trello": return R.drawable.brand_trello;
            case "vercel": return R.drawable.brand_vercel;
            default: return R.drawable.phosphor_storefront_regular;
        }
    }
    /** The mark of a subscription provider, for the rows that pick one. */
    static int provider(String id) {
        switch(id) {
            case "byok": return R.drawable.lucide_key_round;
            case "codex": return R.drawable.brand_codex;
            case "claude": return R.drawable.brand_claude;
            case "gemini": return R.drawable.brand_gemini;
            case "grok": return R.drawable.brand_grok;
            case "opencode": return R.drawable.brand_opencode;
            case "cursor": return R.drawable.brand_cursor;
            default: return R.drawable.phosphor_terminal_window_regular;
        }
    }
}
