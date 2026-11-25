import { CanActivate, ExecutionContext, Injectable, SetMetadata, UnauthorizedException, ForbiddenException } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { JwtService } from '@nestjs/jwt';

export const Roles = (...roles: string[]) => SetMetadata('roles', roles);

/** Verifies the Bearer JWT and (optionally) the @Roles(...) requirement. */
@Injectable()
export class AuthGuard implements CanActivate {
  constructor(private jwt: JwtService, private reflector: Reflector) {}
  async canActivate(ctx: ExecutionContext): Promise<boolean> {
    const req = ctx.switchToHttp().getRequest();
    const h: string = req.headers['authorization'] || '';
    if (!h.startsWith('Bearer ')) throw new UnauthorizedException();
    try { req.user = await this.jwt.verifyAsync(h.slice(7)); } catch { throw new UnauthorizedException(); }
    const roles = this.reflector.get<string[]>('roles', ctx.getHandler()) || this.reflector.get<string[]>('roles', ctx.getClass());
    if (roles && !roles.includes(req.user.role)) throw new ForbiddenException();
    return true;
  }
}
