import { Injectable, ServiceUnavailableException } from '@nestjs/common';
import { createHmac, timingSafeEqual } from 'crypto';
import { PspProvider } from './psp.provider';

/**
 * Production adapter skeleton. Fill the TODOs with your PSP's REST/SDK calls.
 * It refuses to run until every PSP_* variable is present (production stays disabled until approvals are done).
 */
@Injectable()
export class ProductionProvider extends PspProvider {
  readonly mode = 'production' as const;
  private ensure() {
    for (const k of ['PSP_BASE_URL', 'PSP_API_KEY', 'PSP_API_SECRET', 'PSP_WEBHOOK_SECRET'])
      if (!process.env[k]) throw new ServiceUnavailableException('Payment provider is not configured / approved yet');
  }
  async resolve(): Promise<any> { this.ensure(); throw new ServiceUnavailableException('TODO: call PSP VPA/mobile resolution API'); }
  async initiate(): Promise<any> { this.ensure(); throw new ServiceUnavailableException('TODO: call PSP collect/pay API with the idempotency key'); }
  async status(): Promise<any> { this.ensure(); throw new ServiceUnavailableException('TODO: call PSP transaction status API'); }
  async fetchBill(): Promise<any> { this.ensure(); throw new ServiceUnavailableException('TODO: call BBPS operator bill-fetch API'); }
  verifyWebhook(raw: Buffer, sig: string) {
    this.ensure();
    const expected = createHmac('sha256', process.env.PSP_WEBHOOK_SECRET!).update(raw).digest('hex');
    const a = Buffer.from(expected), b = Buffer.from(sig || '');
    return a.length === b.length && timingSafeEqual(a, b);
  }
}
