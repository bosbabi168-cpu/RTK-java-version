sharpen_weapon = {
	cast = function(player)
		local weap = player:getEquippedItem(EQ_WEAP)
		local magic = 60
		if (not player:canCast(0, 1, 0)) then
			return
		end
		if (player.magic < magic) then
			player:sendMinitext("Manamu tidak cukup.")
			return
		end

		if player:checkIfCast(enchants) or player.enchant > 1 then
			player:sendMinitext("Mantra ini sudah aktif.")
			return
		end

		player:sendMinitext(weap.name .. " bersinar oleh cahaya suci.")
		player.magic = player.magic - magic
		player:sendStatus()
		player:sendAction(6, 35)
		player.enchant = 4
		player:sendStatus()
	end,
	recast = function(player)
		player.enchant = 4
		player:sendStatus()
	end,
	uncast = function(player)
		player.enchant = 1
		player:sendStatus()
		player:sendMinitext("Kilauannya meredup jadi denyut, lalu lenyap.")
	end,

	requirements = function(player)
		local level = 99
		local items = {"spike", "electra", "dragons_liver", 0}
		local itemAmounts = {1, 1, 1, 20000}
		local description = "Prepares your weapon for battle."
		return level, items, itemAmounts, description
	end
}
