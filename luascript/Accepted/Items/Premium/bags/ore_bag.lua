ore_bags = {
	-- this not actually an item, is a function that all bags can use

	addToBag = function(player, amberType)
		local item = Item(amberType)
		local bag = player:getInventoryItem(player.invSlot)

		local t = {graphic = item.icon, color = item.iconC}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0

		local amount = 0
		local invAmount = 0

		local invItem
		for i = 0, 52 do
			invItem = player:getInventoryItem(i)
			if invItem ~= nil then
				if invItem.yname == item.yname then
					invAmount = invItem.amount
					break
				end
			end
		end

		if invAmount == 0 then
			player:dialogSeq(
				{
					t,
					"Kau tidak punya " .. Item(amberType).name .. " untuk dimasukkan ke kantong."
				},
				0
			)
			return
		end

		amount = player:inputNumberCheck(player:input("Berapa banyak " .. Item(amberType).name .. " yang ingin kau masukkan ke kantong?"))

		if amount > invAmount then
			amount = invAmount
		end

		if bag.dura == bag.maxDura then
			player:dialogSeq(
				{t, "Kantongmu penuh dengan " .. Item(amberType).name .. "s."},
				0
			)
			return
		end

		if amount + bag.dura > bag.maxDura then
			amount = bag.maxDura - bag.dura
		end

		if player:hasItem(amberType, amount) ~= true then
			return
		end

		bag.dura = bag.dura + amount
		player:removeItem(amberType, amount)
		player:updateInv()
	end,

	removeFromBag = function(player, amberType)
		local bag = player:getInventoryItem(player.invSlot)
		local amount = 0

		if bag.dura > 1 then
			amount = player:inputNumberCheck(player:input("Berapa banyak " .. Item(amberType).name .. " yang ingin kau keluarkan dari kantong?"))
			if amount > bag.dura then
				amount = bag.dura
			end
		elseif bag.dura == 1 then
			amount = 1
		end

		local invAmount = 0
		local diff = 0

		for i = 0, 52 do
			local invItem = player:getInventoryItem(i)

			if invItem ~= nil then
				if invItem.yname == amberType then
					invAmount = invItem.amount
					break
				end
			end
		end

		if invAmount + amount > Item(amberType).stackAmount then
			-- the ambers would be larger than stack
			amount = Item(amberType).stackAmount - invAmount
		end

		if amount == 0 then
			-- stack is 100% full
			player:dialogSeq(
				{
					t,
					"Kau tidak boleh punya lebih banyak " .. Item(amberType).name .. " sampai kau mengeluarkan sebagian dari kantongmu."
				},
				0
			)
			return
		end

		if not player:hasSpace(amberType, amount) then
			player:sendMinitext("Kantongmu penuh.")
			return
		end

		if bag.dura - amount <= 0 then
			if player:hasItem(bag.yname, 1, amount) ~= true then
				return
			end
			player:addItem("empty_ore_bag", 1)
			player:removeItemSlot(player.invSlot, 1)
		else
			bag.dura = bag.dura - amount
			player:updateInv()
		end

		player:addItem(Item(amberType).yname, amount)
	end,

	presentOptions = function(player)
		local bag = player:getInventoryItem(player.invSlot)

		local amberType = string.gsub(bag.yname, "_bag", "")

		local t = {graphic = bag.icon, color = bag.iconC}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0

		local choices = {}
		if bag.dura ~= bag.maxDura then
			table.insert(choices, "Masukkan bijih ke tas.")
		end
		if bag.dura ~= 0 then
			table.insert(choices, "Keluarkan bijih dari tas.")
		end

		local choice = player:menuString("Apa yang ingin kau lakukan?", choices)

		if choice == "Masukkan bijih ke tas." then
			ore_bags.addToBag(player, amberType)
		elseif choice == "Keluarkan bijih dari tas." then
			ore_bags.removeFromBag(player, amberType)
		end
	end
}

empty_ore_bag = {
	use = async(function(player)
		local t = {graphic = convertGraphic(2232, "item"), color = 0}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0

		local ores = {"ore_poor", "ore_med", "ore_high", "ore_very_high"}

		local foundOres = {}
		local amounts = {}

		for i = 0, 52 do
			local item = player:getInventoryItem(i)
			if item ~= nil then
				for j = 1, #ores do
					if item.yname == ores[j] then
						table.insert(foundOres, item.yname)
						table.insert(amounts, item.amount)
					end
				end
			end
		end

		if #foundOres == 0 then
			player:dialogSeq(
				{
					t,
					"Kau harus punya ore di kantongmu untuk bisa menambahkannya ke tas."
				},
				0
			)
			return
		end

		local choiceid = player:buy(
			"What type of ore do you want to add?",
			foundOres,
			amounts,
			{},
			{},
			{},
			{},
			{}
		)

		local invItem

		for i = 0, 52 do
			invItem = player:getInventoryItem(i)
			if invItem ~= nil then
				if invItem.name == choiceid then
					break
				end
			end
		end

		local amount = 0

		if invItem ~= nil then
			-- finally at the point we have a valid object from what the player chose
			if invItem.amount > 1 then
				amount = player:inputNumberCheck(player:input("Berapa banyak " .. invItem.name .. " yang ingin kau masukkan ke kantong bijih?"))

				if amount > invItem.amount then
					amount = invItem.amount
				end
			else
				amount = 1
			end
		end

		if player:hasItem(invItem.yname, amount) ~= true then
			return
		end

		-- this is fail safe incase someone tries swapping slots
		if player:hasItem("empty_ore_bag", 1) ~= true then
			return
		end

		local newBag = invItem.yname

		if not player:hasSpace(newBag .. "_bag", 1) then
			player:sendMinitext("Kantongmu penuh.")
			return
		end

		player:removeItem("empty_ore_bag", 1)
		player:removeItem(invItem.yname, amount)
		player:addItem(newBag .. "_bag", 1, amount)
	end)
}

ore_poor_bag = {
	use = async(function(player)
		ore_bags.presentOptions(player)
	end)
}

ore_med_bag = {
	use = async(function(player)
		ore_bags.presentOptions(player)
	end)
}

ore_high_bag = {
	use = async(function(player)
		ore_bags.presentOptions(player)
	end)
}

ore_very_high_bag = {
	use = async(function(player)
		ore_bags.presentOptions(player)
	end)
}
