import {setDefaultResultOrder} from 'node:dns';
import {setDefaultAutoSelectFamilyAttemptTimeout} from 'node:net';

// Android/proot mobile routes can exceed Node's short default family-attempt
// timeout. Keep normal TLS verification and both IP families; allow each a fair
// connection attempt rather than racing through usable addresses too quickly.
setDefaultResultOrder('ipv4first');
setDefaultAutoSelectFamilyAttemptTimeout(2000);
