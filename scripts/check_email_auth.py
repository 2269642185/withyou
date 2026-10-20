"""Read-only CloudBase email preflight. Never prints SMTP credentials or addresses."""
import argparse
import json
from pathlib import Path

import cloudbase_sql as cloud


def summarize(config, providers):
    email = next((p for p in (providers.get('Data') or []) if p.get('Id') == 'email'), {})
    delivery = email.get('EmailConfig') or {}
    smtp = delivery.get('SmtpConfig') or {}
    custom = delivery.get('On') == 'FALSE'
    return {
        'environment': cloud.ENV,
        'readOnly': True,
        'emailLoginEnabled': config.get('EmailLogin') is True,
        'legacyUsernameLoginEnabled': config.get('UserNameLogin') is True,
        'emailProviderEnabled': email.get('On') == 'TRUE',
        'customSmtpSelected': custom,
        'qqSmtpHost': (smtp.get('ServerHost') or '').lower() == 'smtp.qq.com',
        'sslPort465': smtp.get('ServerPort') == 465,
        'smtpAccountConfigured': bool(smtp.get('AccountUsername')),
        # The platform may redact the password, so its returned value is not evidence
        # of whether a valid authorization code has been configured.
        'smtpAuthorizationVerified': False,
        'realDeliveryVerified': False,
        'globalMailLimitVerified': False,
        'readyToPublish': False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, help='Optional redacted JSON report')
    args = parser.parse_args()
    body = json.dumps({'EnvId': cloud.ENV})
    config = cloud.command('api', 'tcb', 'DescribeLoginConfig', '--body', body, '--json')
    providers = cloud.command('api', 'tcb', 'GetProviders', '--body', body, '--json')
    value = json.dumps(summarize(config, providers), ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(value + '\n', encoding='utf-8')
    print(value)


if __name__ == '__main__':
    main()
