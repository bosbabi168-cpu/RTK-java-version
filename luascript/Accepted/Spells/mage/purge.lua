purge_mage = {
	cast = function(player, target)
		local magic = 30

		if not player:canCast(1, 1, 0) then
			return
		end

		if (player.magic < magic) then
			player:sendMinitext("Manamu tidak cukup.")
			return
		end

		target:removeDuras(venoms)
		player:sendMinitext("Kau merapal Purge.")

		player:sendAction(6, 35)
		player.magic = player.magic - magic
		target:playSound(10)
		target:sendAnimation(10)
		target:sendStatus()
		player:sendStatus()
	end,
	requirements = function(player)
		local level = 30
		local items = {
			Item("gold_acorn").id,
			Item("antler").id,
			Item("mountain_ginseng").id,
			0
		}
		local itemAmounts = {1, 10, 1, 150}
		local description = "Removes poison from target"
		return level, items, itemAmounts, description
	end
}

cure_illness_mage = {
	cast = function(player, target)
		local magic = 30
		if not player:canCast(1, 1, 0) then
			return
		end

		if (player.magic < magic) then
			player:sendMinitext("Manamu tidak cukup.")
			return
		end

		target:removeDuras(venoms)
		player:sendMinitext("Kau merapal Cure illness.")

		player:sendAction(6, 35)
		player.magic = player.magic - magic
		target:playSound(10)
		target:sendAnimation(70)
		target:sendStatus()
		player:sendStatus()
	end,
	requirements = function(player)
		local level = 30
		local items = {
			Item("gold_acorn").id,
			Item("antler").id,
			Item("mountain_ginseng").id,
			0
		}
		local itemAmounts = {1, 10, 1, 150}
		local description = "Removes poison from target"
		return level, items, itemAmounts, description
	end
}

restore_health_mage = {
	cast = function(player, target)
		local magic = 30

		if not player:canCast(1, 1, 0) then
			return
		end

		if (player.magic < magic) then
			player:sendMinitext("Manamu tidak cukup.")
			return
		end

		target:removeDuras(venoms)
		player:sendMinitext("Kau merapal Restore health.")

		player:sendAction(6, 35)
		player.magic = player.magic - magic
		target:playSound(10)
		target:sendAnimation(57)
		target:sendStatus()
		player:sendStatus()
	end,
	requirements = function(player)
		local level = 30
		local items = {
			Item("gold_acorn").id,
			Item("antler").id,
			Item("mountain_ginseng").id,
			0
		}
		local itemAmounts = {1, 10, 1, 150}
		local description = "Removes poison from target"
		return level, items, itemAmounts, description
	end
}

remove_poison_mage = {
	cast = function(player, target)
		local magic = 30

		if not player:canCast(1, 1, 0) then
			return
		end

		if (player.magic < magic) then
			player:sendMinitext("Manamu tidak cukup.")
			return
		end

		target:removeDuras(venoms)
		player:sendMinitext("Kau merapal Remove poison.")

		player:sendAction(6, 35)
		player.magic = player.magic - magic
		target:playSound(10)
		target:sendAnimation(108)
		target:sendStatus()
		player:sendStatus()
	end,
	requirements = function(player)
		local level = 30
		local items = {
			Item("gold_acorn").id,
			Item("antler").id,
			Item("mountain_ginseng").id,
			0
		}
		local itemAmounts = {1, 10, 1, 150}
		local description = "Removes poison from target"
		return level, items, itemAmounts, description
	end
}
