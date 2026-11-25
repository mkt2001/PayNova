import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { PrismaService } from '../common/prisma.service';
import { PspProvider, PspStatus } from './psp.provider';

export const toDto = (t: any) => ({
  id: t.id, counterpartyName: t.counterpartyName, counterpartyVpa: t.counterpartyVpa, amountPaise: Number(t.amountPaise),
  direction: t.direction, status: t.status, method: t.method, note: t.note, category: t.category,
  createdAt: new Date(t.createdAt).getTime(), providerRef: t.providerRef, upiTxnId: t.upiTxnId, maskedAccount: t.maskedAccount,
});

@Injectable()
export class PaymentService implements OnModuleInit {
  private log = new Logger('Payment');
  constructor(private prisma: PrismaService, private psp: PspProvider) {}

  /** Transaction reconciliation: periodically ask the PSP about stale PENDING payments. */
  onModuleInit() {
    setInterval(() => this.reconcile().catch((e) => this.log.warn('reconcile failed: ' + e.message)), 60_000).unref();
  }

  async initiate(userId: string, key: string, b: { vpa: string; name?: string; amountPaise: number; note?: string; method: string; category?: string }) {
    const existing = await this.prisma.transaction.findUnique({ where: { userId_idempotencyKey: { userId, idempotencyKey: key } } });
    if (existing) return toDto(existing); // duplicate submit -> same transaction, never a second payment
    const r = await this.psp.initiate({ idempotencyKey: key, vpa: b.vpa, amountPaise: b.amountPaise, note: b.note, userId });
    try {
      const tx = await this.prisma.transaction.create({ data: {
        userId, idempotencyKey: key, counterpartyName: b.name, counterpartyVpa: b.vpa, amountPaise: BigInt(b.amountPaise),
        method: b.method, note: b.note, category: b.category, providerRef: r.providerRef, status: 'PENDING',
        upiTxnId: r.upiTxnId, maskedAccount: r.maskedAccount } });
      return toDto(tx);
    } catch (e: any) {
      if (e.code === 'P2002') { // lost a race with a parallel request using the same key
        const again = await this.prisma.transaction.findUnique({ where: { userId_idempotencyKey: { userId, idempotencyKey: key } } });
        if (again) return toDto(again);
      }
      throw e;
    }
  }

  /** Only the PSP (webhook or status poll) can move a payment to SUCCESS. */
  async applyStatus(providerRef: string, status: PspStatus, upiTxnId?: string) {
    const tx = await this.prisma.transaction.findUnique({ where: { providerRef } });
    if (!tx || tx.status === status) return tx;
    if (tx.status === 'SUCCESS' && status !== 'REVERSED') return tx;  // no downgrades
    if (tx.status === 'FAILED' || tx.status === 'REVERSED') return tx; // terminal
    const upd = await this.prisma.transaction.update({ where: { id: tx.id }, data: { status, upiTxnId: upiTxnId ?? tx.upiTxnId } });
    await this.prisma.notification.create({ data: { userId: tx.userId, title: `Payment ${status.toLowerCase()}`, body: `₹${Number(tx.amountPaise) / 100} to ${tx.counterpartyName || tx.counterpartyVpa}` } });
    if (status === 'SUCCESS') {
      const coins = Math.floor(Number(tx.amountPaise) / 10000) + 1;
      await this.prisma.rewardLedger.upsert({ where: { userId_txId: { userId: tx.userId, txId: tx.id } }, update: {}, create: { userId: tx.userId, coins, reason: 'Payment', txId: tx.id } });
    }
    return upd;
  }

  async status(userId: string, id: string) {
    const tx = await this.prisma.transaction.findFirst({ where: { OR: [{ id }, { providerRef: id }], userId } });
    if (!tx) return null;
    if (tx.status === 'PENDING') {
      const s = await this.psp.status(tx.providerRef);
      await this.applyStatus(tx.providerRef, s.status, s.upiTxnId);
    }
    return toDto(await this.prisma.transaction.findUnique({ where: { id: tx.id } }));
  }

  async reconcile() {
    const stale = await this.prisma.transaction.findMany({ where: { status: 'PENDING', createdAt: { lt: new Date(Date.now() - 30_000) } }, take: 100 });
    for (const t of stale) {
      const s = await this.psp.status(t.providerRef);
      await this.applyStatus(t.providerRef, s.status, s.upiTxnId);
    }
  }

  async webhook(raw: Buffer, signature: string, body: { eventId: string; providerRef: string; status: PspStatus; upiTxnId?: string }) {
    if (!this.psp.verifyWebhook(raw, signature)) return { ok: false, reason: 'bad signature' };
    try { await this.prisma.webhookEvent.create({ data: { eventId: body.eventId } }); } catch { return { ok: true, duplicate: true } }
    await this.applyStatus(body.providerRef, body.status, body.upiTxnId);
    return { ok: true };
  }
}
