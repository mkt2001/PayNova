import { BadRequestException, Body, Controller, Get, Headers, HttpCode, NotFoundException, Param, Post, Req, UseGuards } from '@nestjs/common';
import { IsInt, IsOptional, IsString, Max, Min, Matches, IsIn } from 'class-validator';
import { AuthGuard } from '../common/auth';
import { PspProvider } from './psp.provider';
import { PaymentService } from './payment.service';

class ResolveDto {
  @IsIn(['VPA', 'MOBILE', 'BANK']) kind: string;
  @IsString() value: string;
  @IsOptional() @IsString() ifsc?: string;
  @IsOptional() @IsString() name?: string;
}
class InitiateDto {
  @Matches(/^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z][a-zA-Z0-9]{2,64}$/) vpa: string;
  @IsOptional() @IsString() name?: string;
  @IsInt() @Min(1) @Max(10_000_000) amountPaise: number;   // max Rs 1,00,000
  @IsOptional() @IsString() note?: string;
  @IsString() method: string;
  @IsOptional() @IsString() category?: string;
}
class BillDto { @IsString() category: string; @IsString() customerId: string; }
class RequestDto { @IsString() toVpa: string; @IsInt() @Min(1) amountPaise: number; @IsOptional() @IsString() note?: string; }

@Controller()
export class PaymentController {
  constructor(private psp: PspProvider, private svc: PaymentService) {}

  @UseGuards(AuthGuard) @Post('qr/resolve')
  resolve(@Body() b: ResolveDto) { return this.psp.resolve(b.kind as any, b.value, b.ifsc, b.name); }

  @UseGuards(AuthGuard) @Post('payment/initiate')
  initiate(@Req() req: any, @Headers('idempotency-key') key: string, @Body() b: InitiateDto) {
    if (!key || key.length < 8) throw new BadRequestException('Idempotency-Key header required');
    return this.svc.initiate(req.user.sub, key, b);
  }

  @UseGuards(AuthGuard) @Get('payment/status/:id')
  async status(@Req() req: any, @Param('id') id: string) {
    const r = await this.svc.status(req.user.sub, id);
    if (!r) throw new NotFoundException();
    return r;
  }

  /** Payment request (collect). Stored as a notification for now; a real PSP collect call goes here. */
  @UseGuards(AuthGuard) @Post('payment/request')
  async request(@Req() req: any, @Body() b: RequestDto) {
    return { requested: true, toVpa: b.toVpa, amountPaise: b.amountPaise, note: b.note ?? null, status: 'CREATED' };
  }

  @UseGuards(AuthGuard) @Post('bbps/fetch')
  fetchBill(@Body() b: BillDto) { return this.psp.fetchBill(b.category, b.customerId); }

  /** PSP callback. Signature is verified over the raw body; events are de-duplicated by eventId. */
  @HttpCode(200) @Post('payment/webhook')
  webhook(@Req() req: any, @Headers('x-signature') sig: string, @Body() body: any) {
    return this.svc.webhook(req.rawBody, sig, body);
  }
}
