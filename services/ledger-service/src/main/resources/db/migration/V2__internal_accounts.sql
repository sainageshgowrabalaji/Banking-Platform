-- The bank's own accounts. Every movement of customer money has one of these on its other side.
--
--   ...0100  Cash and deposits in. The other side of a deposit or a withdrawal
--   ...0101  ACH settlement. Money on its way out through the ACH network
--   ...0102  Instant settlement. Money on its way out through the instant payment network
--
-- The ids are fixed, so other services can name these accounts in their settings.
insert into account (id, customer_id, type, name, currency, status, opened_at)
values ('00000000-0000-0000-0000-000000000100', null, 'INTERNAL', 'Cash and deposits in', 'USD', 'ACTIVE', now()),
       ('00000000-0000-0000-0000-000000000101', null, 'INTERNAL', 'ACH settlement', 'USD', 'ACTIVE', now()),
       ('00000000-0000-0000-0000-000000000102', null, 'INTERNAL', 'Instant settlement', 'USD', 'ACTIVE', now());
