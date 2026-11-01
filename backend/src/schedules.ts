import { randomUUID } from "node:crypto";

export type BotSchedule = {
  id: string; botId: string; text: string; urgent: boolean;
  repeat: "once" | "daily"; timeZone: string; time?: string;
  nextRun: number; created: number; lastRun?: number;
  status: "active" | "completed" | "cancelled" | "failed"; error?: string;
};

function clock(timeZone: string) {
  // Validate the IANA zone, including DST rules; never silently fall back to UTC.
  return new Intl.DateTimeFormat("en-CA", {timeZone, year:"numeric", month:"2-digit", day:"2-digit", hour:"2-digit", minute:"2-digit", hourCycle:"h23"});
}
function local(format: Intl.DateTimeFormat, timestamp: number) {
  const parts=Object.fromEntries(format.formatToParts(timestamp).map(p=>[p.type,p.value]));
  return {day:`${parts.year}-${parts.month}-${parts.day}`,time:`${parts.hour}:${parts.minute}`};
}
export function nextDaily(time: string, timeZone: string, after: number, lastRun?: number) {
  if(!/^([01]\d|2[0-3]):[0-5]\d$/.test(time))throw Error("Use local time HH:mm, for example 17:00");
  const format=clock(timeZone),lastDay=lastRun===undefined?undefined:local(format,lastRun).day;
  // Walk actual minutes across DST transitions. Missing local times are skipped;
  // a repeated clock time fires at most once on that local calendar date.
  const start=Math.floor(after/60000)*60000+60000;
  for(let at=start;at<start+72*3600000;at+=60000){const value=local(format,at);if(value.time===time&&value.day!==lastDay)return at;}
  throw Error("Could not find the next occurrence of this local time");
}
export function makeSchedule(botId:string,input:any,now=Date.now()):BotSchedule {
  if(typeof input.text!=="string"||!input.text.trim()||input.text.length>4000)throw Error("Enter a reminder message of at most 4000 characters");
  const repeat=input.repeat??"once";if(!["once","daily"].includes(repeat))throw Error("Choose once or daily");
  const timeZone=input.timezone??Intl.DateTimeFormat().resolvedOptions().timeZone;
  if(typeof timeZone!=="string")throw Error("Enter an IANA timezone");clock(timeZone);
  let nextRun:number;
  if(repeat==="daily"){
    if(input.delay_seconds!==undefined||input.at!==undefined)throw Error("Daily schedules use time (HH:mm) and timezone, not a delay or date");
    nextRun=nextDaily(input.time,timeZone,now);
  }else{
    if((input.delay_seconds!==undefined)===(input.at!==undefined))throw Error("Choose exactly one: delay_seconds or at (ISO date with timezone)");
    if(input.delay_seconds!==undefined){
      if(!Number.isFinite(input.delay_seconds)||input.delay_seconds<1||input.delay_seconds>366*86400)throw Error("Use a delay between one second and one year");
      nextRun=now+input.delay_seconds*1000;
    }else{
      if(typeof input.at!=="string"||!/(Z|[+-]\d{2}:\d{2})$/i.test(input.at))throw Error("Use an ISO date with an explicit timezone offset");
      nextRun=Date.parse(input.at);
      if(!Number.isFinite(nextRun)||nextRun<=now)throw Error("Choose a valid future date and time");
    }
  }
  return {id:randomUUID(),botId,text:input.text.trim(),urgent:input.urgent!==false,repeat,timeZone,time:repeat==="daily"?input.time:undefined,nextRun,created:now,status:"active"};
}
