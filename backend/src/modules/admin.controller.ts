import { Body, Controller, Get, Param, Post, Put, Delete, Query, Req, UseGuards } from '@nestjs/common';
import { IsBoolean, IsOptional, IsString } from 'class-validator';
import { AuthGuard, Roles } from '../common/auth';
import { PrismaService } from '../common/prisma.service';
import { PspProvider } from '../payment/psp.provider';
import { toDto } from '../payment/payment.service';

class OfferDto { @IsString() title: string; @IsString() body: string; @IsOptional() @IsBoolean() active?: boolean; }
class TicketUpdate { @IsString() status: string; }
class BroadcastDto { @IsString() title: string; @IsString() body: string; }

/** Admin API. Never returns password hashes, PINs or banking credentials; every write is audit-logged. */
@UseGuards(AuthGuard) @Roles('ADMIN', 'SUPERADMIN') @Controller('admin')
export class AdminController {
  constructor(private prisma: PrismaService, private psp: PspProvider) {}
  private audit(req: any, action: string, target?: string) { return this.prisma.auditLog.create({ data: { actorId: req.user.sub, action, target } }); }

  @Get('users') async users(@Req() req: any, @Query('q') q?: string) {
    await this.audit(req, 'LIST_USERS');
    return this.prisma.user.findMany({ where: q ? { OR: [{ email: { contains: q } }, { name: { contains: q } }] } : {}, select: { id: true, email: true, name: true, role: true, createdAt: true }, take: 200, orderBy: { createdAt: 'desc' } });
  }
  @Get('transactions') async tx(@Query('status') status?: string) {
    return (await this.prisma.transaction.findMany({ where: status ? { status: status as any } : {}, orderBy: { createdAt: 'desc' }, take: 200 })).map(toDto);
  }
  @Get('provider') provider() {
    const configured = ['PSP_BASE_URL', 'PSP_API_KEY', 'PSP_API_SECRET', 'PSP_WEBHOOK_SECRET'].every((k) => !!process.env[k]);
    return { mode: this.psp.mode, configured, productionEnabled: this.psp.mode === 'production' && configured };
  }
  @Get('failed') async failed() { return (await this.prisma.transaction.findMany({ where: { status: { in: ['FAILED', 'REVERSED'] } }, orderBy: { createdAt: 'desc' }, take: 200 })).map(toDto); }
  @Get('analytics') async analytics() {
    const [users, tx, success, failed, pending] = await Promise.all([
      this.prisma.user.count(), this.prisma.transaction.count(), this.prisma.transaction.count({ where: { status: 'SUCCESS' } }),
      this.prisma.transaction.count({ where: { status: 'FAILED' } }), this.prisma.transaction.count({ where: { status: 'PENDING' } })]);
    return { users, transactions: tx, success, failed, pending };
  }
  @Get('offers') offers() { return this.prisma.offer.findMany({ orderBy: { createdAt: 'desc' } }); }
  @Post('offers') async addOffer(@Req() req: any, @Body() b: OfferDto) { await this.audit(req, 'CREATE_OFFER', b.title); return this.prisma.offer.create({ data: b }); }
  @Put('offers/:id') async upOffer(@Req() req: any, @Param('id') id: string, @Body() b: OfferDto) { await this.audit(req, 'UPDATE_OFFER', id); return this.prisma.offer.update({ where: { id }, data: b }); }
  @Delete('offers/:id') async delOffer(@Req() req: any, @Param('id') id: string) { await this.audit(req, 'DELETE_OFFER', id); return this.prisma.offer.delete({ where: { id } }); }
  @Get('tickets') tickets() { return this.prisma.supportTicket.findMany({ orderBy: { createdAt: 'desc' }, take: 200 }); }
  @Put('tickets/:id') async ticket(@Req() req: any, @Param('id') id: string, @Body() b: TicketUpdate) { await this.audit(req, 'UPDATE_TICKET', id); return this.prisma.supportTicket.update({ where: { id }, data: { status: b.status } }); }
  @Post('notify') async notify(@Req() req: any, @Body() b: BroadcastDto) {
    await this.audit(req, 'BROADCAST', b.title);
    const users = await this.prisma.user.findMany({ select: { id: true } });
    await this.prisma.notification.createMany({ data: users.map((u) => ({ userId: u.id, title: b.title, body: b.body })) });
    return { sent: users.length }; // TODO: also push via Firebase Cloud Messaging using stored device tokens
  }
  @Get('audit') audits() { return this.prisma.auditLog.findMany({ orderBy: { createdAt: 'desc' }, take: 200 }); }
}

// Public offers feed for the app (no auth needed for active offers).
@Controller('offers')
export class OffersPublicController {
  constructor(private prisma: PrismaService) {}
  @Get() list() { return this.prisma.offer.findMany({ where: { active: true }, orderBy: { createdAt: 'desc' } }); }
}
