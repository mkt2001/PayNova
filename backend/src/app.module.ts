import { Module } from '@nestjs/common';
import { APP_GUARD, Reflector } from '@nestjs/core';
import { JwtModule } from '@nestjs/jwt';
import { ThrottlerGuard, ThrottlerModule } from '@nestjs/throttler';
import { PrismaService } from './common/prisma.service';
import { AuthGuard } from './common/auth';
import { AuthController } from './auth/auth.controller';
import { PaymentService } from './payment/payment.service';
import { PaymentController } from './payment/payment.controller';
import { PspProvider } from './payment/psp.provider';
import { SandboxProvider } from './payment/sandbox.provider';
import { ProductionProvider } from './payment/production.provider';
import { UserController, TransactionsController, BeneficiaryController, RewardsController, NotificationsController, SupportController, AnalyticsController } from './modules/api.controllers';
import { AdminController, OffersPublicController } from './modules/admin.controller';

@Module({
  imports: [
    JwtModule.register({ global: true, secret: process.env.JWT_SECRET, signOptions: { expiresIn: '1h' } }),
    // In-memory limiter by default. For multi-instance deployments plug a Redis storage (REDIS_URL) here.
    ThrottlerModule.forRoot([{ ttl: 60_000, limit: 100 }]),
  ],
  controllers: [AuthController, PaymentController, UserController, TransactionsController, BeneficiaryController, RewardsController, NotificationsController, SupportController, AnalyticsController, AdminController, OffersPublicController],
  providers: [
    PrismaService, AuthGuard, Reflector, PaymentService,
    { provide: APP_GUARD, useClass: ThrottlerGuard },
    {
      provide: PspProvider,
      // Production is selected ONLY when explicitly requested; the adapter itself still refuses until configured.
      useFactory: () => (process.env.PROVIDER_MODE === 'production' ? new ProductionProvider() : new SandboxProvider()),
    },
  ],
})
export class AppModule {}
