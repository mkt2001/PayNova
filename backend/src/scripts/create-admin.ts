// Usage: npx ts-node src/scripts/create-admin.ts admin@example.com 'StrongPassword!'
import { PrismaClient } from '@prisma/client';
import * as bcrypt from 'bcrypt';

(async () => {
  const [email, password] = process.argv.slice(2);
  if (!email || !password || password.length < 12) { console.error('Usage: create-admin <email> <password (12+ chars)>'); process.exit(1); }
  const p = new PrismaClient();
  await p.user.upsert({ where: { email: email.toLowerCase() }, update: { role: 'SUPERADMIN' }, create: { email: email.toLowerCase(), passwordHash: await bcrypt.hash(password, 12), role: 'SUPERADMIN', name: 'Admin' } });
  console.log('Admin ready:', email);
  await p.$disconnect();
})();
