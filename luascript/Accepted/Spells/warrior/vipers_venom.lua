vipers_venom_warrior = {
	cast = function(player)
		local magic = 5000
		if not player:canCast(1, 1, 0) then
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
		player.enchant = 4
		player:sendStatus()
		player:sendAction(6, 35)
	end,
	recast = function(player)
		player.enchant = 4
		player:sendStatus()
	end,
	uncast = function(player)
		player.enchant = 1
		player:sendStatus()
	end,
	requirements = function(player)
		local level = 99
		local items = {
			Item("il_san_spike").id,
			Item("ice_sabre").id,
			Item("dragons_liver").id,
			0
		}
		local itemAmounts = {1, 2, 2, 40000}
		local description = "Infuses your weapon with intensely strong energy."
		return level, items, itemAmounts, description
	end
}
