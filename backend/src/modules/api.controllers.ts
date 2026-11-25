import { Body, Controller, Get, Param, Post, Put, Query, Req, UseGuards, NotFoundException } from '@nestjs/common';
import { IsBoolean, IsOptional, IsString, Matches } from 'class-validator';
import { AuthGuard } from '../common/auth';
import { PrismaService } from '../common/prisma.service';
import { toDto } from '../payment/payment.service';

class DeviceDto { @IsString() fcmToken: string; @IsOptional() @IsString() platform?: string; }
class ProfileDto { @IsOptional() @IsString() name?: string; @IsOptional() @Matches(/^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z][a-zA-Z0-9]{2,64}$/) upiId?: string; @IsOptional() @IsBoolean() contactsConsent?: boolean; }
class BeneficiaryDto { @IsString() name: string; @IsString() vpa: string; @IsOptional() @IsBoolean() favourite?: boolean; }
class TicketDto { @IsString() message: string; }

@UseGuards(AuthGuard) @Controller('user')
export class UserController {
  constructor(private prisma: PrismaService) {}
  @Get('profile') async profile(@Req() req: any) {
    const u = await this.prisma.user.findUnique({ where: { id: req.user.sub }, select: { id: true, email: true, name: true, mobile: true, upiId: true, contactsConsent: true, createdAt: true } });
    if (!u) throw new NotFoundException(); return u;
  }
  @Put('profile') update(@Req() req: any, @Body() b: ProfileDto) { return this.prisma.user.update({ where: { id: req.user.sub }, data: b, select: { id: true, name: true, upiId: true, contactsConsent: true } }); }
  /** Linked bank accounts come from the PSP (multiple accounts). Masked identifiers only. */
  @Get('banks') banks() { return []; /* TODO: PSP "list accounts" API; return masked account ids only */ }
  @Post('devices') device(@Req() req: any, @Body() b: DeviceDto) {
    return this.prisma.device.upsert({ where: { userId_fcmToken: { userId: req.user.sub, fcmToken: b.fcmToken } }, update: {}, create: { userId: req.user.sub, fcmToken: b.fcmToken, platform: b.platform } });
  }
}

@UseGuards(AuthGuard) @Controller('transactions')
export class TransactionsController {
  constructor(private prisma: PrismaService) {}
  @Get() async list(@Req() req: any, @Query('status') status?: string, @Query('take') take = '50', @Query('cursor') cursor?: string) {
    const rows = await this.prisma.transaction.findMany({
      where: { userId: req.user.sub, ...(status ? { status: status as any } : {}) }, orderBy: { createdAt: 'desc' },
      take: Math.min(parseInt(take) || 50, 200), ...(cursor ? { skip: 1, cursor: { id: cursor } } : {}) });
    return rows.map(toDto);
  }
  @Get(':id') async one(@Req() req: any, @Param('id') id: string) {
    const t = await this.prisma.transaction.findFirst({ where: { id, userId: req.user.sub } });
    if (!t) throw new NotFoundException(); return toDto(t);
  }
}

@UseGuards(AuthGuard) @Controller('beneficiaries')
export class BeneficiaryController {
  constructor(private prisma: PrismaService) {}
  @Get() list(@Req() req: any) { return this.prisma.beneficiary.findMany({ where: { userId: req.user.sub } }); }
  @Post() save(@Req() req: any, @Body() b: BeneficiaryDto) {
    return this.prisma.beneficiary.upsert({ where: { userId_vpa: { userId: req.user.sub, vpa: b.vpa } }, update: { name: b.name, favourite: b.favourite ?? false }, create: { userId: req.user.sub, ...b } });
  }
}

@UseGuards(AuthGuard) @Controller('rewards')
export class RewardsController {
  constructor(private prisma: PrismaService) {}
  @Get() async get(@Req() req: any) {
    const agg = await this.prisma.rewardLedger.aggregate({ where: { userId: req.user.sub }, _sum: { coins: true } });
    return { coins: agg._sum.coins ?? 0 };
  }
}

@UseGuards(AuthGuard) @Controller('notifications')
export class NotificationsController {
  constructor(private prisma: PrismaService) {}
  @Get() list(@Req() req: any) { return this.prisma.notification.findMany({ where: { userId: req.user.sub }, orderBy: { createdAt: 'desc' }, take: 100 }); }
  @Post('read') read(@Req() req: any) { return this.prisma.notification.updateMany({ where: { userId: req.user.sub, read: false }, data: { read: true } }); }
}

@UseGuards(AuthGuard) @Controller('support')
export class SupportController {
  constructor(private prisma: PrismaService) {}
  @Post('tickets') create(@Req() req: any, @Body() b: TicketDto) { return this.prisma.supportTicket.create({ data: { userId: req.user.sub, message: b.message } }); }
  @Get('tickets') mine(@Req() req: any) { return this.prisma.supportTicket.findMany({ where: { userId: req.user.sub }, orderBy: { createdAt: 'desc' } }); }
}

@UseGuards(AuthGuard) @Controller('analytics')
export class AnalyticsController {
  constructor(private prisma: PrismaService) {}
  /** Monthly expense report by category, from verified (SUCCESS) payments only. */
  @Get('monthly') async monthly(@Req() req: any, @Query('month') month?: string) {
    const d = month ? new Date(month + '-01T00:00:00Z') : new Date(Date.UTC(new Date().getUTCFullYear(), new Date().getUTCMonth(), 1));
    const to = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 1));
    const rows = await this.prisma.transaction.groupBy({ by: ['category'], where: { userId: req.user.sub, status: 'SUCCESS', direction: 'SENT', createdAt: { gte: d, lt: to } }, _sum: { amountPaise: true } });
    return rows.map((r) => ({ category: r.category ?? 'Others', totalPaise: Number(r._sum.amountPaise ?? 0) }));
  }
}
