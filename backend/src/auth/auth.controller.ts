import { Body, Controller, Post, UnauthorizedException, ConflictException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { Throttle } from '@nestjs/throttler';
import * as bcrypt from 'bcrypt';
import { IsEmail, IsOptional, IsString, MinLength } from 'class-validator';
import { PrismaService } from '../common/prisma.service';

class RegisterDto {
  @IsEmail() email: string;
  @IsString() @MinLength(8) password: string;
  @IsOptional() @IsString() name?: string;
}
class LoginDto {
  @IsEmail() email: string;
  @IsString() password: string;
}

@Controller('auth')
export class AuthController {
  constructor(private prisma: PrismaService, private jwt: JwtService) {}

  @Throttle({ default: { limit: 5, ttl: 60_000 } })
  @Post('register')
  async register(@Body() b: RegisterDto) {
    if (await this.prisma.user.findUnique({ where: { email: b.email.toLowerCase() } })) throw new ConflictException('Email already registered');
    const u = await this.prisma.user.create({ data: { email: b.email.toLowerCase(), name: b.name, passwordHash: await bcrypt.hash(b.password, 12) } }); // role is always USER
    return { accessToken: await this.jwt.signAsync({ sub: u.id, role: u.role }) };
  }

  @Throttle({ default: { limit: 5, ttl: 60_000 } })
  @Post('login')
  async login(@Body() b: LoginDto) {
    const u = await this.prisma.user.findUnique({ where: { email: b.email.toLowerCase() } });
    if (!u || !(await bcrypt.compare(b.password, u.passwordHash))) throw new UnauthorizedException('Invalid credentials');
    return { accessToken: await this.jwt.signAsync({ sub: u.id, role: u.role }) };
  }
}
