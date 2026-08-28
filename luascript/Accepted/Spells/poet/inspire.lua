inspire_poet = {
	cast = function(player, target)
		local magicCost = 30
		if target.blType ~= BL_PC then
			return
		end
		target:sendStatus()
		local mana = target.maxMagic - target.magic

		if not player:canCast(1, 1, 0) then
			return
		end

		if (target.state == 1) then
			player:sendMinitext("Itu tidak bisa menyelamatkannya lagi.")
			return
		end

		if player.magic < magicCost then
			player:sendMinitext("Mana tidak cukup.")
			return
		end
		if target.ID == player.ID then
			player:sendMinitext("Tidak berhasil.")
			return
		end

		if target.blType == BL_PC then
			if player.magic < mana then
				target.magic = target.magic + player.magic
				player.magic = 0
			else
				player.magic = player.magic - mana
				target.magic = target.magic + mana
			end

			target:sendAnimation(11)
			target:sendStatus()
			target:sendMinitext(player.name .. " merapal Inspire padamu.")
			player:sendAction(6, 20)
			player:playSound(22)
			player:sendMinitext("Kau merapal Inspire.")
			player:sendStatus()
		end
	end,

	requirements = function(player)
		local level = 45
		local items = {Item("gold_acorn").id, Item("amethyst").id, 0}
		local itemAmounts = {1, 1, 100}
		local description = "Restore mana to a target."
		return level, items, itemAmounts, description
	end
}

share_energy_poet = {
	cast = function(player, target)
		local magicCost = 30
		if target.blType ~= BL_PC then
			return
		end
		target:sendStatus()
		local mana = target.maxMagic - target.magic

		if not player:canCast(1, 1, 0) then
			return
		end

		if (target.state == 1) then
			player:sendMinitext("Itu tidak bisa menyelamatkannya lagi.")
			return
		end

		if player.magic < magicCost then
			player:sendMinitext("Mana tidak cukup.")
			return
		end
		if target.ID == player.ID then
			player:sendMinitext("Tidak berhasil.")
			return
		end

		if target.blType == BL_PC then
			if player.magic < mana then
				target.magic = target.magic + player.magic
				player.magic = 0
			else
				player.magic = player.magic - mana
				target.magic = target.magic + mana
			end

			target:sendAnimation(33)
			target:sendStatus()
			target:sendMinitext(player.name .. " merapal Share Energy padamu.")
			player:sendAction(6, 20)
			player:playSound(22)
			player:sendMinitext("Kau merapal Share Energy.")
			player:sendStatus()
		end
	end,

	requirements = function(player)
		local level = 45
		local items = {Item("gold_acorn").id, Item("amethyst").id, 0}
		local itemAmounts = {1, 1, 100}
		local description = "Restore mana to a target."
		return level, items, itemAmounts, description
	end
}

bestow_power_poet = {
	cast = function(player, target)
		local magicCost = 30
		if target.blType ~= BL_PC then
			return
		end
		target:sendStatus()
		local mana = target.maxMagic - target.magic

		if not player:canCast(1, 1, 0) then
			return
		end

		if (target.state == 1) then
			player:sendMinitext("Itu tidak bisa menyelamatkannya lagi.")
			return
		end

		if player.magic < magicCost then
			player:sendMinitext("Mana tidak cukup.")
			return
		end
		if target.ID == player.ID then
			player:sendMinitext("Tidak berhasil.")
			return
		end

		if target.blType == BL_PC then
			if player.magic < mana then
				target.magic = target.magic + player.magic
				player.magic = 0
			else
				player.magic = player.magic - mana
				target.magic = target.magic + mana
			end

			target:sendAnimation(70)
			target:sendStatus()
			target:sendMinitext(player.name .. " merapal Bestow Power padamu.")
			player:sendAction(6, 20)
			player:playSound(22)
			player:sendMinitext("Kau merapal Bestow Power.")
			player:sendStatus()
		end
	end,

	requirements = function(player)
		local level = 45
		local items = {Item("gold_acorn").id, Item("amethyst").id, 0}
		local itemAmounts = {1, 1, 100}
		local description = "Restore mana to a target."
		return level, items, itemAmounts, description
	end
}

release_focus_poet = {
	cast = function(player, target)
		local magicCost = 30
		if target.blType ~= BL_PC then
			return
		end
		target:sendStatus()
		local mana = target.maxMagic - target.magic

		if not player:canCast(1, 1, 0) then
			return
		end

		if (target.state == 1) then
			player:sendMinitext("Itu tidak bisa menyelamatkannya lagi.")
			return
		end

		if player.magic < magicCost then
			player:sendMinitext("Mana tidak cukup.")
			return
		end
		if target.ID == player.ID then
			player:sendMinitext("Tidak berhasil.")
			return
		end

		if target.blType == BL_PC then
			if player.magic < mana then
				target.magic = target.magic + player.magic
				player.magic = 0
			else
				player.magic = player.magic - mana
				target.magic = target.magic + mana
			end

			target:sendAnimation(49)
			target:sendStatus()
			target:sendMinitext(player.name .. " merapal Release Focus padamu.")
			player:sendAction(6, 20)
			player:playSound(22)
			player:sendMinitext("Kau merapal Release Focus.")
			player:sendStatus()
		end
	end,

	requirements = function(player)
		local level = 45
		local items = {Item("gold_acorn").id, Item("amethyst").id, 0}
		local itemAmounts = {1, 1, 100}
		local description = "Restore mana to a target."
		return level, items, itemAmounts, description
	end
}
