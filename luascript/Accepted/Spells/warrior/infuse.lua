infuse_warrior = {
	cast = function(player)
		local weap = player:getEquippedItem(EQ_WEAP)
		local magic = 90
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

		player:sendAction(6, 35)
		player:sendMinitext(weap.name .. " bersinar oleh cahaya suci.")
		player.magic = player.magic - magic
		player.enchant = 2
		player:sendStatus()
	end,
	recast = function(player)
		player.enchant = 2
		player:sendStatus()
	end,
	uncast = function(player)
		player.enchant = 1
		player:sendStatus()
		player:sendMinitext("Kilauannya meredup jadi denyut, lalu lenyap.")
	end,
	requirements = function(player)
		local level = 55
		local items = {
			Item("antler").id,
			Item("red_fox_fur").id,
			Item("steel_saber").id,
			0
		}
		local itemAmounts = {10, 20, 1, 1000}
		local description = "Infuses your weapon with strong energy, causing you to deal more damage."
		return level, items, itemAmounts, description
	end
}

tincture_of_the_unknown_warrior = {
	cast = function(player)
		local weap = player:getEquippedItem(EQ_WEAP)
		local magic = 90
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

		player:sendAction(6, 35)
		player:sendMinitext(weap.name .. " bersinar oleh cahaya suci.")
		player.magic = player.magic - magic
		player.enchant = 2
		player:sendStatus()
	end,
	recast = function(player)
		player.enchant = 2
		player:sendStatus()
	end,
	uncast = function(player)
		player.enchant = 1
		player:sendStatus()
		player:sendMinitext("Kilauannya meredup jadi denyut, lalu lenyap.")
	end,
	requirements = function(player)
		local level = 55
		local items = {
			Item("antler").id,
			Item("red_fox_fur").id,
			Item("steel_saber").id,
			0
		}
		local itemAmounts = {10, 20, 1, 1000}
		local description = "Infuses your weapon with strong energy, causing you to deal more damage."
		return level, items, itemAmounts, description
	end
}

tigers_claw_warrior = {
	cast = function(player)
		local weap = player:getEquippedItem(EQ_WEAP)
		local magic = 90
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

		player:sendAction(6, 35)
		player:sendMinitext(weap.name .. " bersinar oleh cahaya suci.")
		player.magic = player.magic - magic
		player.enchant = 2
		player:sendStatus()
	end,
	recast = function(player)
		player.enchant = 2
		player:sendStatus()
	end,
	uncast = function(player)
		player.enchant = 1
		player:sendStatus()
		player:sendMinitext("Kilauannya meredup jadi denyut, lalu lenyap.")
	end,
	requirements = function(player)
		local level = 55
		local items = {
			Item("antler").id,
			Item("red_fox_fur").id,
			Item("steel_saber").id,
			0
		}
		local itemAmounts = {10, 20, 1, 1000}
		local description = "Infuses your weapon with strong energy, causing you to deal more damage."
		return level, items, itemAmounts, description
	end
}

whetstone_warrior = {
	cast = function(player)
		local weap = player:getEquippedItem(EQ_WEAP)
		local magic = 90
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

		player:sendAction(6, 35)
		player:sendMinitext(weap.name .. " bersinar oleh cahaya suci.")
		player.magic = player.magic - magic
		player.enchant = 2
		player:sendStatus()
	end,
	recast = function(player)
		player.enchant = 2
		player:sendStatus()
	end,
	uncast = function(player)
		player.enchant = 1
		player:sendStatus()
		player:sendMinitext("Kilauannya meredup jadi denyut, lalu lenyap.")
	end,
	requirements = function(player)
		local level = 55
		local items = {
			Item("antler").id,
			Item("red_fox_fur").id,
			Item("steel_saber").id,
			0
		}
		local itemAmounts = {10, 20, 1, 1000}
		local description = "Infuses your weapon with strong energy, causing you to deal more damage."
		return level, items, itemAmounts, description
	end
}
