baekhos_blade_rogue = {
	cast = function(player)
		local weap = player:getEquippedItem(EQ_WEAP)
		local magic = 6000
		if (not player:canCast(1, 1, 0)) then
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

		player:sendAction(6, 35)
		player:sendMinitext(weap.name .. " bersinar oleh cahaya suci.")
		player.magic = player.magic - magic
		player.enchant = 2.25
		player:sendStatus()
	end,
	recast = function(player)
		player.enchant = 2.25
		player:sendStatus()
	end,
	uncast = function(player)
		player.enchant = 1
		player:sendStatus()
		player:sendMinitext("Kilauannya meredup jadi denyut, lalu lenyap.")
	end,
	requirements = function(player)
		local level = 99
		local items = {"ee_san_blood", "dark_dagger", "magical_dust", 0}
		local itemAmounts = {1, 4, 4, 60000}
		local description = "Infuse your weapon with energy, causing you to deal more damage."
		return level, items, itemAmounts, description
	end
}
