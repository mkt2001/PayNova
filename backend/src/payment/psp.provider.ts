export type PspStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'REVERSED';
export interface Resolved { name?: string; vpa: string; verified: boolean; merchantInfo?: string }
export interface InitiateInput { idempotencyKey: string; vpa: string; amountPaise: number; note?: string; userId: string }
export interface InitiateResult { providerRef: string; status: PspStatus; upiTxnId?: string; maskedAccount?: string }

/** Implement this for a real authorized UPI PSP. Secrets come from env, never from the Android app. */
export abstract class PspProvider {
  abstract readonly mode: 'sandbox' | 'production';
  abstract resolve(kind: 'VPA' | 'MOBILE' | 'BANK', value: string, ifsc?: string, name?: string): Promise<Resolved>;
  abstract initiate(i: InitiateInput): Promise<InitiateResult>;
  abstract status(providerRef: string): Promise<{ status: PspStatus; upiTxnId?: string; maskedAccount?: string }>;
  abstract fetchBill(category: string, customerId: string): Promise<{ billerName: string; customerName?: string; dueDate?: string; amountPaise: number }>;
  /** Verify the PSP's webhook signature over the RAW body. */
  abstract verifyWebhook(rawBody: Buffer, signature: string): boolean;
}
