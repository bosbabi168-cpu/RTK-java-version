nomadic_blade = {
	cast = function(player)
		local magic = 10000
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

		player:sendMinitext("Senjatamu bersinar oleh cahaya suci.")
		player.magic = player.magic - magic
		player.enchant = 8
		player:sendStatus()
		player:sendAction(6, 35)
	end,

	recast = function(player)
		player.enchant = 8
	end,
	uncast = function(player)
		player.enchant = 1
	end,
	requirements = function(player)
		local level = 99
		local items = {"ee_san_spike", "electra", "dragons_liver", 0}
		local itemAmounts = {1, 4, 4, 60000}
		local description = "Prepares your weapon for battle."
		return level, items, itemAmounts, description
	end
}
