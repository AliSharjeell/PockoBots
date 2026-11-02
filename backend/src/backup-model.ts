import {defaults,publicSettings,validateSettings,type Settings,type Store} from './storage.js';

/** Owner-provisioned, app-private backup. No credential is bundled in source/APKs. */
export class BackupModel {
 private profile:Settings|null=null;private enabled=false;
 constructor(private store:Store){}
 async init(){const saved=await this.store.read<{profile:Settings|null;enabled:boolean}>('backup-model',{profile:null,enabled:false});this.profile=saved.profile?validateSettings(saved.profile,defaults):null;this.enabled=saved.enabled===true&&!!this.profile;}
 current(primary:Settings){return this.enabled&&this.profile?this.profile:primary;}
 redact(text:string){return this.profile?.apiKey?text.split(this.profile.apiKey).join('[redacted]'):text;}
 view(primary:Settings){return {...publicSettings(primary),backupAvailable:!!this.profile?.apiKey,backupEnabled:this.enabled,backupModel:this.profile?.model||'',hasActiveKey:!!this.current(primary).apiKey,activeModel:this.current(primary).model};}
 async configure(input:any){const profile=validateSettings({...input,executionProvider:'byok'},defaults);if(!profile.apiKey)throw Error('A backup key is required');this.profile=profile;await this.save();}
 async toggle(enabled:unknown){if(typeof enabled!=='boolean')throw Error('Choose whether to use the backup');if(enabled&&!this.profile?.apiKey)throw Error('No backup connection has been provisioned on this device');this.enabled=enabled;await this.save();}
 private save(){return this.store.write('backup-model',{profile:this.profile,enabled:this.enabled});}
}
