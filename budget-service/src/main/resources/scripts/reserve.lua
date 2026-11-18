-- Reserve budget atomically.
-- Redis runs this whole script as ONE command, so no other request
-- can change the budget between the check and the update.
--
-- KEYS[1] = budget hash, e.g. "budget:acme"  (fields: limit, used, reserved)
-- ARGV[1] = amount to reserve, e.g. "50"
-- Returns 1 = reserved, 0 = not enough budget (or no budget for this tenant)

local amount = tonumber(ARGV[1])

-- No budget set up for this tenant: refuse (safe default)
if redis.call('EXISTS', KEYS[1]) == 0 then
    return 0
end

local limit    = tonumber(redis.call('HGET', KEYS[1], 'limit'))
local used     = tonumber(redis.call('HGET', KEYS[1], 'used'))
local reserved = tonumber(redis.call('HGET', KEYS[1], 'reserved'))

if limit - used - reserved >= amount then
    redis.call('HINCRBY', KEYS[1], 'reserved', amount)
    return 1
end

return 0
